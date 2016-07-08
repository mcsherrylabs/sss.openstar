package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block.DistributeTx
import sss.asado.MessageKeys
import sss.asado.ledger._
import sss.asado.ledger.LedgerItem
import sss.asado.network.NetworkMessage
import sss.asado.util.SeqSerializer

import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 3/18/16.
  */
class TxWriter(writeConfirmActor: ActorRef) extends Actor with ActorLogging {

  log.info("TxWriter actor has started...")

  override def receive: Receive = working(None)

  override def postStop = log.warning(s"Tx Writer ($self) is down."); super.postStop

  private def writeStx(blockLedger: BlockChainLedger, signedTx: LedgerItem): Unit = {
      val sendr = sender()
      log.info(s"Will attempt to reach sender $sendr")
      Try(blockLedger(signedTx)) match {
        case Success(btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>
          sendr ! NetworkMessage(MessageKeys.SignedTxAck, btx.toId.toBytes)
          writeConfirmActor ! DistributeTx(sendr, btx)

        case Failure(e) =>
          val id = e match {
            case LedgerException(ledgerId, _) => ledgerId
            case _ => 0.toByte
          }
          log.info(s"Failed to ledger tx! ${signedTx.txIdHexStr} ${e.getMessage}")
          sendr ! NetworkMessage(MessageKeys.SignedTxNack, TxMessage(id, signedTx.txId, e.getMessage).toBytes)

      }
    }


  def errorNoLedger(txId: TxId): Unit = {
    val msg = "No ledger open, retry later."
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.SignedTxNack, TxMessage(0.toByte, txId, msg).toBytes)
  }

  def errorBadMessage: Unit = {
    val msg = "Cannot deserialise that message, wrong code for the bytes?"
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.MalformedMessage, msg.getBytes)
  }

  private def working(blockLedgerOpt: Option[BlockChainLedger]): Receive = {

    case BlockLedger(blockChainActor: ActorRef, blockLedger: Option[BlockChainLedger]) => {
      context.become(working(blockLedger))
      blockChainActor ! AcknowledgeNewLedger

    }

    case NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>

      blockLedgerOpt match {
        case Some(blockLedger) => SeqSerializer.fromBytes(bytes) foreach { stx =>
          writeStx(blockLedger, stx.toLedgerItem)
        }
        case None => SeqSerializer.fromBytes(bytes).foreach(stx => errorNoLedger(stx.toLedgerItem.txId))
      }


    case NetworkMessage(MessageKeys.SignedTx, bytes) =>

      Try(bytes.toLedgerItem) match {
        case Success(stx) => blockLedgerOpt match {
          case Some(blockLedger) => writeStx(blockLedger, stx)
          case None => errorNoLedger(stx.txId)
        }
        case Failure(e) => errorBadMessage
      }

  }

}
