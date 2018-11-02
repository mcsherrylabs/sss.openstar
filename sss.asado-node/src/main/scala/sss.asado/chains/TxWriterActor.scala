package sss.asado.chains


import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import sss.ancillary.Logging
import sss.asado._
import sss.asado.account.NodeIdentity
import sss.asado.actor.SystemPanic
import sss.asado.block.BlockChainLedger.NewBlockId
import sss.asado.block.{Block, BlockChain, BlockChainLedger, BlockChainSignaturesAccessor, BlockClosedEvent}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumFollowersSyncedMonitor.SyncedQuorum
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

  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[BlockClosedEvent])
  messageEventBus.subscribe(classOf[SyncedQuorum])
  messageEventBus.subscribe(classOf[QuorumLost])


  //private var quorum: Option[Quorum] = None
  private var lastHeightClosed: Long = 0

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

    case sq @ SyncedQuorum(`chainId`, _) =>
      //start listening for
      messageEventBus.subscribe(MessageKeys.SignedTx)
      messageEventBus.subscribe(MessageKeys.SeqSignedTx)
      messageEventBus.subscribe(classOf[InternalLedgerItem])

      setTimer()
      val l = createLedger()
      log.info(s"We are leader ({}) and accepting transactions (at height {}) ...", thisNodeId, l.blockHeight)

      lastHeightClosed = l.blockHeight - 1
      context become acceptTxs(l, sq)

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

      context become waitForUp
  }


  override def receive: Receive = waitForUp

  def createBlockCloseDistributingActor(
                                         ledger: BlockChainLedger,
                                         height: Long
                                       ): ActorRef =
    BlockCloseDistributorActor(
      BlockCloseDistributorActor.props(
        height,
        ledger,
        bc,
        blockChainSettings,
        nodeIdentity
      )
    )


  def createTxDistributingActor(bTx: BlockChainTx): ActorRef =
    TxDistributorActor(
      TxDistributorActor.props(bTx)
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

  private def acceptTxs(blockLedger: BlockChainLedger, sq: SyncedQuorum): Receive = stopOnAllStop orElse reset orElse {

    case BlockClosedEvent(heightClosed) =>

      log.info("{} now closed, previous was {}", heightClosed, lastHeightClosed)

      if(heightClosed > lastHeightClosed)
        lastHeightClosed = heightClosed

    case ConnectionLost(nodeId) if sq.members contains nodeId =>
      val updated = sq.copy(syncs = sq.syncs.filterNot(_.nodeId == nodeId))
      blockTxsToDistribute.values.flatten foreach (_ ! updated)
      context become acceptTxs(blockLedger, updated)

    case newSq@SyncedQuorum(`chainId`, _) =>
      // send to all children?
      blockTxsToDistribute.values.flatten foreach (_ ! newSq)
      context become acceptTxs(blockLedger, newSq)

    case c @ CloseBlock(height) =>
      createBlockCloseDistributingActor(blockLedger, height) ! sq

    case PostJournalConfirm(bcTx) =>

      postJournalConfirm(blockChainSettings.maxTxPerBlock,
        createTxDistributingActor,
        InternalResponse(None),
        sq,
        bcTx)

    case BlockCloseTrigger =>
      blockCloseTimer foreach (_.cancel())
      blockCloseTimer = None
      blocksToClose += blockLedger.blockHeight
      checkConditionsForBlockClose()

      if(lastHeightClosed + 3 >= blockLedger.blockHeight) {
        val newLedger = BlockChainLedger(blockLedger.blockHeight + 1)

        ledgers.coinbase(nodeIdentity, newLedger.blockHeight) foreach {

          validateAndJournalTx(blockChainSettings.maxTxPerBlock,
            newLedger,
            _,
            createTxDistributingActor,
            InternalResponse(None),
            sq
          )
        }

        context become acceptTxs(newLedger, sq)

        setTimer()
      } else {
        log.info("MAXXED OUT DUDE!")
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.SeqSignedTx,
                         clientNodeId,
                         stxs: SeqLedgerItem) =>

      stxs.value foreach { stx =>
        validateAndJournalTx(blockChainSettings.maxTxPerBlock,
          blockLedger,
          stx,
          createTxDistributingActor,
          NetResponse(clientNodeId, send),
          sq
        )
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.SignedTx,
                         clientNodeId,
                         stx: LedgerItem) =>

      validateAndJournalTx(blockChainSettings.maxTxPerBlock,
        blockLedger,
        stx,
        createTxDistributingActor,
        NetResponse(clientNodeId, send),
        sq
      )



    case InternalLedgerItem(`chainId`, signedTx, responseListener) =>
      validateAndJournalTx(blockChainSettings.maxTxPerBlock,
        blockLedger,
        signedTx,
        createTxDistributingActor,
        InternalResponse(responseListener),
        sq
      )

    case nack: TxNack => //the quorum has rejected a tx the leader has accepted

      val blockLedger = BlockChainLedger(nack.bTx.height)
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

      val blockLedger = BlockChainLedger(bTx.height)
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
          val bId = BlockId(bTx.height, bTx.blockTx.index)
          responseMap(bId).confirm(bTx.toId)
          events foreach (messageEventBus.publish)
          messageEventBus.publish(NewBlockId(bId))

      }

  }

  override def postStop = log.warning(s"Tx Writer ($self) is down.")


  private def validateAndJournalTx(maxTxPerBlock: Int,
                                   blockLedger: BlockChainLedger,
                                   signedTx: LedgerItem,
                                   createConfirmingActor: BlockChainTx => ActorRef,
                                   responder: Response,
                                   sq: SyncedQuorum): Unit = {

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
        postJournalConfirm(maxTxPerBlock, createConfirmingActor, responder, sq: SyncedQuorum, bcTx)
    }

  }


  private def postJournalConfirm(maxTxPerBlock: Int,
                                 createConfirmingActor: BlockChainTx => ActorRef,
                                 responder: Response,
                                 sq: SyncedQuorum,
                                 bcTx: BlockChainTx) = {

    val confirmingRefs = blockTxsToDistribute(bcTx.height)
    val confirmingActor = createConfirmingActor(bcTx)
    confirmingActor ! sq
    val height = bcTx.height
    val index = bcTx.blockTx.index

    blockTxsToDistribute += height -> (confirmingActor +: confirmingRefs)
    responseMap += BlockId(height, index) -> responder

    if (index >= maxTxPerBlock) {
      self ! BlockCloseTrigger
    }
  }
}
