package sss.asado.chains


import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props, Terminated}
import sss.ancillary.Logging
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.block.{BlockChain, BlockChainLedger, BlockChainSignatures, BlockChainTxConfirms}
import sss.asado.chains.BlockCloseDistributorActor.{CloseBlock, ProcessCoinBaseHook}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumFollowersSyncedMonitor.LocalBlockChainUp
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.chains.TxDistributorActor.{TxConfirmed, TxReplicated}
import sss.asado.chains.TxWriterActor._
import sss.asado.common.block._
import sss.asado.ledger.{LedgerItem, _}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.db.Db

import scala.collection.SortedSet
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

/**
  * Created by alan on 3/18/16.
  */
object TxWriterActor {

  private case object BlockCloseTrigger

  case class InternalLedgerItem(chainId: GlobalChainIdMask,
                                le: LedgerItem,
                                responseListener: Option[ActorRef])
      extends AsadoEvent

  case class InternalConfirm(chainId: GlobalChainIdMask, blTxId: BlockChainTxId)
    extends AsadoEvent
  case class InternalAck(chainId: GlobalChainIdMask, blTxId: BlockChainTxId)
      extends AsadoEvent
  case class InternalNack(chainId: GlobalChainIdMask, txMsg: TxMessage)
      extends AsadoEvent

  def apply(checkedProps: CheckedProps)(implicit actorSystem: ActorSystem): Unit = {
    actorSystem.actorOf(checkedProps.value)
  }

  sealed trait Response {
    def nack(id: Byte, msg: String, txId: TxId): Unit
    def ack(bTx: BlockChainTxId): Unit
    def confirm(bTx: BlockChainTxId): Unit
  }

  private case class InternalResponse(listener: Option[ActorRef])(
      implicit chainId: GlobalChainIdMask)
      extends Response with Logging {

    override def nack(id: GlobalChainIdMask, msg: String, txId: TxId): Unit =
      listener match {
        case None => log.warn(s"Internal tx has been nacked -> $msg")
        case Some(listener) =>  listener ! InternalNack(chainId, TxMessage(id, txId, msg))
      }

    override def ack(bTx: BlockChainTxId): Unit =
      listener foreach (_ ! InternalAck(chainId, bTx))

    override def confirm(bTx: BlockChainTxId): Unit =
      listener foreach (_ ! InternalConfirm(chainId, bTx))
  }

  private case class NetResponse(nodeId: UniqueNodeIdentifier, send: Send)(
      implicit chainId: GlobalChainIdMask)
      extends Response {

    override def nack(id: Byte, msg: String, txId: TxId): Unit =
      send(MessageKeys.SignedTxNack,
                             TxMessage(id, txId, msg),
           nodeId)

    override def ack(bTx: BlockChainTxId): Unit = {
      send(MessageKeys.SignedTxAck, bTx, nodeId)
    }

    override def confirm(bTx: BlockChainTxId): Unit =
      send(MessageKeys.SignedTxConfirm, bTx, nodeId)
  }


  case class CheckedProps(value:Props) extends AnyVal

  def props(blockChainSettings: BlockChainSettings,
            thisNodeId: UniqueNodeIdentifier,
            bc: BlockChain with BlockChainTxConfirms with BlockChainSignatures,
            processCoinBaseHook: ProcessCoinBaseHook)(implicit db: Db,
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
        processCoinBaseHook,
        db,
        chainId,
        send,
        messageEventBus,
        ledgers
        )
    )
  }

}

private class TxWriterActor(blockChainSettings: BlockChainSettings,
                            thisNodeId: UniqueNodeIdentifier,
                            bc: BlockChain with BlockChainTxConfirms with BlockChainSignatures,
                            processCoinBaseHook: ProcessCoinBaseHook)(implicit val db: Db,
                                                                      chainId: GlobalChainIdMask,
                                                                      send: Send,
                                                                      messageEventBus: MessageEventBus,
                                                                      ledgers: Ledgers)
    extends Actor
    with ActorLogging {

  log.info("TxWriter actor has started...")

  import context.dispatcher

  messageEventBus.subscribe(classOf[QuorumLost])
  messageEventBus.subscribe(classOf[LocalBlockChainUp])

  private var responseMap = Map[BlockId, Response]()

  var blockHeightsToDistribute: Map[Long, Seq[ActorRef]] =
    Map().withDefaultValue(Seq())

  private var blockCloseTimer: Option[Cancellable] = None

  private def createLedger(blockHeightIncrement: Int = 1): BlockChainLedger = {
    val newBlockheight = bc.lastBlockHeader.height + blockHeightIncrement
    BlockChainLedger(newBlockheight)
  }

  private def setTimer(): Unit = {

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

  private def waitForUp(q: Quorum): Receive = reset orElse {

    case LocalBlockChainUp(`chainId`, `thisNodeId`) =>
      //start listening for
      log.info(s"The leader is $thisNodeId (me)")

      messageEventBus.subscribe(MessageKeys.SignedTx)
      messageEventBus.subscribe(MessageKeys.SeqSignedTx)
      messageEventBus.subscribe(classOf[InternalLedgerItem])
      log.info("We are leader and accepting transactions...")
      setTimer()

      context become acceptTxs(createLedger(), q)
  }

  private def reset: Receive = {

    case QuorumLost(`chainId`) =>
      log.info(s"We are no longer accepting transactions :( ")
      messageEventBus.unsubscribe(MessageKeys.SignedTx)
      messageEventBus.unsubscribe(MessageKeys.SeqSignedTx)
      messageEventBus.unsubscribe(classOf[InternalLedgerItem])
      context become waitForQuorum
  }

  private def waitForQuorum: Receive = {

    case q: Quorum =>
      context become waitForUp(q)

  }

  override def receive: Receive = waitForQuorum

  def createBlockCloseDistributingActor(
                                         q: Quorum,
                                         send: Send,
                                         ledger: BlockChainLedger): ActorRef =
    BlockCloseDistributorActor(
      BlockCloseDistributorActor.props(
        ledger,
        q,
        messageEventBus,
        send,
        processCoinBaseHook
      )
    )


  def createTxDistributingActor(
      q: Quorum
  )(bTx: BlockChainTx): ActorRef =
    TxDistributorActor(
      TxDistributorActor.props(bTx, q)
    )

  private var allBlockTxsDistributed: SortedSet[Long] = SortedSet[Long]()
  private var blocksToClose: SortedSet[Long] = SortedSet[Long]()

  private def checkConditionsForBlockClose(): Unit = {

    (allBlockTxsDistributed.headOption, blocksToClose.headOption) match {

      case (Some(allDistributed),Some(closeTriggered)) if allDistributed == closeTriggered =>
        //remove both and fire close message
        allBlockTxsDistributed = allBlockTxsDistributed.tail
        blocksToClose = blocksToClose.tail
        self ! CloseBlock(closeTriggered)

      case (h1, h2) => log.debug(s"checkConditionsForBlockClose failed with $h1 $h2")
    }
  }

  private def acceptTxs(blockLedger: BlockChainLedger, q: Quorum): Receive = {


    case c @ CloseBlock(height) =>
      createBlockCloseDistributingActor(q, send, blockLedger) ! c

    case BlockCloseTrigger =>
      blockCloseTimer map (_.cancel())
      blockCloseTimer = None
      blocksToClose += blockLedger.blockHeight
      checkConditionsForBlockClose()
      context become acceptTxs(createLedger(), q)
      setTimer()

    case IncomingMessage(`chainId`,
                         MessageKeys.SeqSignedTx,
                         clientNodeId,
                         stxs: Seq[LedgerItem]) =>

      stxs foreach { stx =>
        writeSignedTx(blockChainSettings.maxTxPerBlock,
          blockLedger,
          stx,
          createTxDistributingActor(q),
          NetResponse(clientNodeId, send))
      }

    case IncomingMessage(`chainId`,
                         MessageKeys.SignedTx,
                         clientNodeId,
                         stx: LedgerItem) =>

      writeSignedTx(blockChainSettings.maxTxPerBlock,
        blockLedger,
        stx,
        createTxDistributingActor(q),
        NetResponse(clientNodeId, send)
      )



    case InternalLedgerItem(`chainId`, signedTx, responseListener) =>
      writeSignedTx(blockChainSettings.maxTxPerBlock,
        blockLedger,
        signedTx,
        createTxDistributingActor(q),
        InternalResponse(responseListener)
      )

    case Terminated(ref) =>
      blockHeightsToDistribute = blockHeightsToDistribute(blockLedger.blockHeight) match {
        case Seq(ref) =>
          allBlockTxsDistributed += blockLedger.blockHeight
          // A block has all it's TXs distributed to the quorum, it may be waiting on this to close.
          checkConditionsForBlockClose()
          blockHeightsToDistribute - blockLedger.blockHeight

        case others =>
          blockHeightsToDistribute + (blockLedger.blockHeight -> (others filterNot (_ == ref)))
      }


    case TxReplicated(bTx, c) =>
      //TODO skipping the cancel rollback point in the DB, it should be able to rollback if the Tx
      //TODO is never confirmed
      sender() ! TxConfirmed(bTx, c)
      responseMap(BlockId(bTx.height, bTx.blockTx.index)).confirm(bTx.toId)

  }

  override def postStop = log.warning(s"Tx Writer ($self) is down.");
  super.postStop

  private def writeSignedTx(maxTxPerBlock: Int,
                            blockLedger: BlockChainLedger,
                            signedTx: LedgerItem,
                            createConfirmingActor: BlockChainTx => ActorRef,
                            responder: Response): Unit = {

    Try(blockLedger(signedTx)) match {
      case Success(btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>
        responder.ack(btx.toId)
        val confirmingRefs = blockHeightsToDistribute(height)
        val confirmingActor = createConfirmingActor(btx)
        context watch confirmingActor
        blockHeightsToDistribute += height -> (confirmingActor +: confirmingRefs)
        responseMap += BlockId(height, index) -> responder

        if(index >= maxTxPerBlock) {
          self ! BlockCloseTrigger
        }

      case Failure(e) =>
        val id = e match {
          case LedgerException(ledgerId, _) => ledgerId
          case _                            => 0.toByte
        }
        log.info(s"Failed to ledger tx! ${signedTx.txIdHexStr} ${e.getMessage}")
        responder.nack(id, e.getMessage, signedTx.txId)
    }
  }


}
