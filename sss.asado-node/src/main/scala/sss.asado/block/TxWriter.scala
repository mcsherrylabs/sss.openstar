package sss.asado.block

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.MessageKeys
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block._
import sss.asado.ledger.{LedgerItem, _}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.SerializedMessage
import sss.asado.util.SeqSerializer

import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 3/18/16.
  */
class TxWriter(chainId: GlobalChainIdMask, writeConfirmActor: ActorRef) extends Actor with ActorLogging {

  log.info("TxWriter actor has started...")

  //FIXME or delete me
  import SerializedMessage.noChain

  override def receive: Receive = working(None)

  override def postStop = log.warning(s"Tx Writer ($self) is down."); super.postStop

  private def writeStx(blockLedger: BlockChainLedger, signedTx: LedgerItem): Unit = {
      val sendr = sender()
      Try(blockLedger(signedTx)) match {
        case Success(btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>
          sendr ! SerializedMessage(chainId, MessageKeys.SignedTxAck, btx.toId.toBytes)
          writeConfirmActor ! DistributeTx(sendr, btx)

        case Failure(e) =>
          val id = e match {
            case LedgerException(ledgerId, _) => ledgerId
            case _ => 0.toByte
          }
          log.info(s"Failed to ledger tx! ${signedTx.txIdHexStr} ${e.getMessage}")
          sendr ! SerializedMessage(MessageKeys.SignedTxNack, TxMessage(id, signedTx.txId, e.getMessage).toBytes)

      }
    }


  def errorNoLedger(txId: TxId): Unit = {
    val msg = "No ledger open, retry later."
    log.error(msg)

    sender() ! SerializedMessage(MessageKeys.TempNack, TxMessage(0.toByte, txId, msg).toBytes)
  }

  def errorBadMessage: Unit = {
    val msg = "Cannot deserialise that message, wrong code for the bytes?"
    log.error(msg)
    sender() ! SerializedMessage(MessageKeys.MalformedMessage, msg.getBytes(StandardCharsets.UTF_8))
  }

  private def working(blockLedgerOpt: Option[BlockChainLedger]): Receive = {

    case BlockLedger(blockChainActor: ActorRef, blockLedger: Option[BlockChainLedger]) => {
      context.become(working(blockLedger))
      blockChainActor ! AcknowledgeNewLedger
    }

    case IncomingMessage(_, MessageKeys.SeqSignedTx, _, stxs: Seq[Array[Byte]]) =>
      blockLedgerOpt match {
        case Some(blockLedger) => stxs foreach { stx =>
          writeStx(blockLedger, stx.toLedgerItem)
        }
        case None => stxs.foreach(stx => errorNoLedger(stx.toLedgerItem.txId))
      }


    case SerializedMessage(_, MessageKeys.SeqSignedTx, bytes) =>

      blockLedgerOpt match {
        case Some(blockLedger) => SeqSerializer.fromBytes(bytes) foreach { stx =>
          writeStx(blockLedger, stx.toLedgerItem)
        }
        case None => SeqSerializer.fromBytes(bytes).foreach(stx => errorNoLedger(stx.toLedgerItem.txId))
      }


    case IncomingMessage(_, _, _, stx: LedgerItem) =>
      blockLedgerOpt match {
        case Some(blockLedger) => writeStx(blockLedger, stx)
        case None => errorNoLedger(stx.txId)
      }

    case SerializedMessage(_, MessageKeys.SignedTx, bytes) =>

      Try(bytes.toLedgerItem) match {
        case Success(stx) => blockLedgerOpt match {
          case Some(blockLedger) => writeStx(blockLedger, stx)
          case None => errorNoLedger(stx.txId)
        }
        case Failure(e) => errorBadMessage
      }

  }

}
