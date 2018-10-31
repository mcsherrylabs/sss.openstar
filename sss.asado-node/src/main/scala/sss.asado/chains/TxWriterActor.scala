package sss.asado.chains


import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import sss.ancillary.Logging
import sss.asado._
import sss.asado.account.NodeIdentity
import sss.asado.actor.SystemPanic
import sss.asado.block.{Block, BlockChain, BlockChainLedger, BlockChainSignaturesAccessor}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.chains.TxDistributorActor.{apply => _, _}
import sss.asado.chains.TxWriterActor._
import sss.asado.common.block.{TxMessage, _}
import sss.asado.ledger.{LedgerItem, _}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.db.Db

import scala.collection.SortedSet
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

/**
  * Created by alan on 3/18/16.
  */
object TxWriterActor {

  final private case class CloseBlock(height: Long)
  final private case object BlockCloseTrigger
  final private case class PostJournalConfirm(bcTx: BlockChainTx)

  sealed trait InternalTxResult extends AsadoEvent {
    val chainId: GlobalChainIdMask
  }

  case class InternalLedgerItem(chainId: GlobalChainIdMask,
                                le: LedgerItem,
                                responseListener: Option[ActorRef]) extends AsadoEvent {
    override def toString: String = {
      s"InternalLedgerItem(ChainId:$chainId, $le, listener:${responseListener.map(_.path.name)})"
    }
  }


  case class InternalCommit(chainId: GlobalChainIdMask, blTxId: BlockChainTxId)
    extends InternalTxResult
  case class InternalAck(chainId: GlobalChainIdMask, blTxId: BlockChainTxId)
      extends InternalTxResult
  case class InternalTempNack(chainId: GlobalChainIdMask, txMsg: TxMessage)
    extends InternalTxResult
  case class InternalNack(chainId: GlobalChainIdMask, txMsg: TxMessage)
      extends InternalTxResult


  def apply(checkedProps: CheckedProps)(implicit actorSystem: ActorSystem): Unit = {
    actorSystem.actorOf(checkedProps.value.withDispatcher("blocking-dispatcher"), checkedProps.name)
  }

  sealed trait Response {
    def tempNack(txMsg: TxMessage): Unit
    def nack(txMsg: TxMessage): Unit
    def nack(id: Byte, msg: String, txId: TxId): Unit
    def ack(bTx: BlockChainTxId): Unit
    def confirm(bTx: BlockChainTxId): Unit
  }

  case class InternalResponse(listener: Option[ActorRef])(
      implicit chainId: GlobalChainIdMask)
      extends Response with Logging {

    override def tempNack(txMsg: TxMessage): Unit =
      listener match {
        case None => log.warn(s"Internal tx has been temp nacked -> ${txMsg.msg}")
        case Some(listener) =>  listener ! InternalTempNack(chainId, txMsg)
      }

    override def nack(txMsg: TxMessage): Unit =
      listener match {
        case None => log.warn(s"Internal tx has been nacked -> ${txMsg.msg}")
        case Some(listener) =>  listener ! InternalNack(chainId, txMsg)
      }

    override def nack(id: GlobalChainIdMask, msg: String, txId: TxId): Unit =
      nack(TxMessage(id, txId, msg))

    override def ack(bTx: BlockChainTxId): Unit =
      listener foreach (_ ! InternalAck(chainId, bTx))

    override def confirm(bTx: BlockChainTxId): Unit =
      listener foreach (_ ! InternalCommit(chainId, bTx))
  }

  case class NetResponse(nodeId: UniqueNodeIdentifier, send: Send)(
      implicit chainId: GlobalChainIdMask)
      extends Response {

    override def tempNack(txMsg: TxMessage): Unit =
      send(MessageKeys.TempNack,
        txMsg,
        nodeId)

    override def nack(txMsg: TxMessage): Unit =
      send(MessageKeys.SignedTxNack,
        txMsg,
        nodeId)

    override def nack(id: Byte, msg: String, txId: TxId): Unit =
      nack(TxMessage(id, txId, msg))


    override def ack(bTx: BlockChainTxId): Unit = {
      send(MessageKeys.SignedTxAck, bTx, nodeId)
    }

    override def confirm(bTx: BlockChainTxId): Unit =
      send(MessageKeys.SignedTxConfirm, bTx, nodeId)
  }


  case class CheckedProps(value:Props, name:String)

  def props(blockChainSettings: BlockChainSettings,
            thisNodeId: UniqueNodeIdentifier,
            bc: BlockChain with BlockChainSignaturesAccessor,
            nodeIdentity: NodeIdentity)(implicit db: Db,
                                                                  chainId: GlobalChainIdMask,
                                                                  send: Send,
                                                                  messageEventBus: MessageEventBus,
                                                                   ledgers: Ledgers
                                                                  ): CheckedProps = {
    CheckedProps(
      Props(classOf[TxWriterActor],
        blockChainSettings,
        thisNodeId,
        bc,
        nodeIdentity,
        db,
        chainId,
        send,
        messageEventBus,
        ledgers
        ),
      s"TxWriterActor_$chainId"
    )
  }

}

private class TxWriterActor(blockChainSettings: BlockChainSettings,
                            thisNodeId: UniqueNodeIdentifier,
                            bc: BlockChain with BlockChainSignaturesAccessor,
                            nodeIdentity: NodeIdentity
                           )(implicit val db: Db,
                                                        chainId: GlobalChainIdMask,
                                                        send: Send,
                                                        messageEventBus: MessageEventBus,
                                                        ledgers: Ledgers)
    extends Actor
    with SystemPanic
    with ActorLogging {

  log.info("TxWriter actor has started...")

  messageEventBus.subscribe(classOf[Quorum])

  private var quorum: Option[Quorum] = None

  private var responseMap = Map[BlockId, Response]()

  var blockTxsToDistribute: Map[Long, Seq[ActorRef]] =
    Map().withDefaultValue(Seq())

  private var blockCloseTimer: Option[Cancellable] = None

  private var blocksToClose: SortedSet[Long] = SortedSet[Long]()

  private def createLedger(blockHeightIncrement: Int = 1): BlockChainLedger = {
    val newBlockheight = bc.lastBlockHeader.height + blockHeightIncrement
    BlockChainLedger(newBlockheight)
  }

  private def setTimer(): Unit = {

    import context.dispatcher

    blockCloseTimer = Option(
    context.
      system.
      scheduler.
      scheduleOnce(blockChainSettings.maxBlockOpenSecs seconds,
        self,
        BlockCloseTrigger
      )
    )
  }

  private def waitForUp: Receive = reset orElse {

    case q: Quorum =>
      quorum = Option(q)

    case BlockChainReady(`chainId`, `thisNodeId`) =>
      //start listening for
      messageEventBus.subscribe(MessageKeys.SignedTx)
      messageEventBus.subscribe(MessageKeys.SeqSignedTx)
      messageEventBus.subscribe(classOf[InternalLedgerItem])

      setTimer()
      val l = createLedger()
      log.info(s"We are leader ({}) and accepting transactions (at height {}) ...", thisNodeId, l.blockHeight)

      context become acceptTxs(l)

      Block(l.blockHeight).getUnCommitted foreach (leftover => {

        log.info("Found uncommitted tx, redistributing... BlockId(h:{},index:{}) ",
          l.blockHeight,
          leftover.index
        )

        self ! PostJournalConfirm(BlockChainTx(l.blockHeight, leftover))
      })

  }

  private def reset: Receive = {

    case QuorumLost(`chainId`) =>
      log.info(s"Quorum lost {} is not accepting transactions :( ", thisNodeId)
      messageEventBus.unsubscribe(MessageKeys.SignedTx)
      messageEventBus.unsubscribe(MessageKeys.SeqSignedTx)
      messageEventBus.unsubscribe(classOf[InternalLedgerItem])
      messageEventBus.unsubscribe(classOf[QuorumLost])
      messageEventBus.unsubscribe(classOf[BlockChainReady])
      context become waitForQuorum
  }

  private def waitForQuorum: Receive = {

    case q: Quorum =>
      quorum = Option(q)
      context become waitForUp
      messageEventBus.subscribe(classOf[QuorumLost])
      messageEventBus.subscribe(classOf[BlockChainReady])

  }

  override def receive: Receive = waitForQuorum

  def createBlockCloseDistributingActor(
                                         q: Quorum,
                                         ledger: BlockChainLedger,
                                         height: Long
                                       ): ActorRef =
    BlockCloseDistributorActor(
      BlockCloseDistributorActor.props(
        height,
        ledger,
        q,

        bc,
        blockChainSettings,
        nodeIdentity
      )
    )


  def createTxDistributingActor(
      q: Quorum
  )(bTx: BlockChainTx): ActorRef =
    TxDistributorActor(
      TxDistributorActor.props(bTx, q)
    )

  private def checkConditionsForBlockClose(): Unit = {
    blocksToClose
      .headOption
      .foreach (checkConditionsForBlockClose)
  }

  private def checkConditionsForBlockClose(heightOfBlockToClose: Long): Unit = {

    log.debug("Checking if we can close block {}", heightOfBlockToClose)
    if (log.isDebugEnabled) {
      if (blocksToClose.size > 1) log.debug("Blocks left to close {}", blocksToClose)
      if (blockTxsToDistribute.nonEmpty) log.debug("Block Txs to distribute {}", blockTxsToDistribute)
    }


    if(blockTxsToDistribute(heightOfBlockToClose).isEmpty) {
        blocksToClose = blocksToClose.tail
        self ! CloseBlock(heightOfBlockToClose)

    } else {
      log.debug(s"checkConditionsForBlockClose failed for $heightOfBlockToClose")
    }
  }

  private def acceptTxs(blockLedger: BlockChainLedger): Receive = stopOnAllStop orElse reset orElse {

    case q: Quorum =>
      quorum = Option(q)

    case c @ CloseBlock(height) =>
      createBlockCloseDistributingActor(quorum.get, blockLedger, height)

    case PostJournalConfirm(bcTx) =>
      postJournalConfirm(blockChainSettings.maxTxPerBlock, createTxDistributingActor(quorum.get), InternalResponse(None), bcTx)

    case BlockCloseTrigger =>
      blockCloseTimer foreach (_.cancel())
      blockCloseTimer = None
      blocksToClose += blockLedger.blockHeight
      checkConditionsForBlockClose()

      val newLedger = BlockChainLedger(blockLedger.blockHeight + 1)

      ledgers.coinbase(nodeIdentity, newLedger.blockHeight) foreach {

        validateAndJournalTx(blockChainSettings.maxTxPerBlock,
          newLedger,
          _,
          createTxDistributingActor(quorum.get),
          InternalResponse(None)
        )
      }

      context become acceptTxs(newLedger)

      setTimer()

    case IncomingMessage(`chainId`,
                         MessageKeys.SeqSignedTx,
                         clientNodeId,
                         stxs: SeqLedgerItem) =>

      stxs.value foreach { stx =>
        validateAndJournalTx(blockChainSettings.maxTxPerBlock,
          blockLedger,
          stx,
          createTxDistributingActor(quorum.get),
          NetResponse(clientNodeId, send))
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.SignedTx,
                         clientNodeId,
                         stx: LedgerItem) =>

      validateAndJournalTx(blockChainSettings.maxTxPerBlock,
        blockLedger,
        stx,
        createTxDistributingActor(quorum.get),
        NetResponse(clientNodeId, send)
      )



    case InternalLedgerItem(`chainId`, signedTx, responseListener) =>
      validateAndJournalTx(blockChainSettings.maxTxPerBlock,
        blockLedger,
        signedTx,
        createTxDistributingActor(quorum.get),
        InternalResponse(responseListener)
      )

    case nack: TxNack => //the quorum has rejected a tx the leader has accepted

      blockLedger.rejected(nack.bTx) match {
        case Failure(e) =>
          log.error("Could not reject tx ")
          systemPanic(e)
        case Success(_) =>
          val blockId = BlockId(nack.bTx.height, nack.bTx.blockTxId.index)
          responseMap(blockId).nack(0,
            "Tx couldn't replicate (possibly time out)", nack.bTx.blockTxId.txId)

          val refOfTxDistributor = sender()
          refOfTxDistributor ! TxRejected(nack.bTx, nack.rejectors)
          blockTxsToDistribute(blockLedger.blockHeight) match {
            case Seq(`refOfTxDistributor`) =>
              // A block has all it's TXs distributed to the quorum, it may be waiting on this to close.
              blockTxsToDistribute -= blockLedger.blockHeight
              checkConditionsForBlockClose()

            case others =>
              blockTxsToDistribute += (blockLedger.blockHeight -> (others filterNot (_ == refOfTxDistributor)))
          }
      }

    case TxReplicated(bTx) =>

      blockLedger.commit(bTx.blockTx) match {
        case Failure(e) =>
          log.error("Could not apply tx after confirm")
          systemPanic(e)
        case Success(events) =>
          val refOfTxDistributor = sender()
          refOfTxDistributor ! TxCommitted(bTx.toId)
          blockTxsToDistribute(bTx.height) match {
            case Seq(`refOfTxDistributor`) =>
              // A block has all it's TXs distributed to the quorum, it may be waiting on this to close.
              blockTxsToDistribute -= bTx.height
              checkConditionsForBlockClose()

            case others =>
              blockTxsToDistribute += (bTx.height -> (others filterNot (_ == refOfTxDistributor)))
          }
          responseMap(BlockId(bTx.height, bTx.blockTx.index)).confirm(bTx.toId)
          events foreach (messageEventBus.publish)
      }

  }

  override def postStop = log.warning(s"Tx Writer ($self) is down.")


  private def validateAndJournalTx(maxTxPerBlock: Int,
                                   blockLedger: BlockChainLedger,
                                   signedTx: LedgerItem,
                                   createConfirmingActor: BlockChainTx => ActorRef,
                                   responder: Response): Unit = {

    blockLedger.validate(signedTx) match {
      case Failure(e) =>
        val id = e match {
          case LedgerException(ledgerId, _) => ledgerId
          case _                            => 0.toByte
        }
        log.info(s"Failed to ledger tx! ${signedTx.txIdHexStr} ${e.getMessage}")
        responder.nack(id, e.getMessage, signedTx.txId)

      case Success((bcTx :BlockChainTx, _)) =>

        val t = blockLedger.journal(bcTx.blockTx)
        assert(t.blockTx == bcTx.blockTx, "Journalled blockTx did not equal validated blockTx")
        assert(t.height == blockLedger.blockHeight, "Sanity check for block heights failed")
        responder.ack(bcTx.toId)
        postJournalConfirm(maxTxPerBlock, createConfirmingActor, responder, bcTx)
    }

  }


  private def postJournalConfirm(maxTxPerBlock: Int,
                                 createConfirmingActor: BlockChainTx => ActorRef,
                                 responder: Response,
                                 bcTx: BlockChainTx) = {

    val confirmingRefs = blockTxsToDistribute(bcTx.height)
    val confirmingActor = createConfirmingActor(bcTx)
    val height = bcTx.height
    val index = bcTx.blockTx.index

    blockTxsToDistribute += height -> (confirmingActor +: confirmingRefs)
    responseMap += BlockId(height, index) -> responder

    if (index >= maxTxPerBlock) {
      self ! BlockCloseTrigger
    }
  }
}
