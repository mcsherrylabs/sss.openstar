package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block.DistributeTx
import com.google.common.primitives.Longs
import ledger._
import sss.asado.MessageKeys
import sss.asado.ledger.Ledger
import sss.asado.network.NetworkMessage

import scala.util.{Failure, Success}
/**
  * Created by alan on 3/18/16.
  */

class TxWriter(writeConfirmActor: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = working(None)

  override def postStop = log.warning(s"Tx Writer ($self) is down."); super.postStop

  private def writeStx(blockLedger: Ledger, signedTx: SignedTx): Unit = {
      blockLedger(signedTx) match {
        case Success(TxDbId(height)) =>
          val sendr = sender()
          sendr ! NetworkMessage(MessageKeys.SignedTxAck, Longs.toByteArray(height))
          writeConfirmActor ! DistributeTx(sendr, signedTx, height)
        case Failure(e) => {
          log.error(e, s"Failed to apply tx! ${e.getMessage}")
          sender() ! NetworkMessage(MessageKeys.SignedTxNack, e.getMessage.getBytes)
        }
      }
    }


  def errorNoLedger: Unit = {
    val msg = "No ledger in play, cannnot handle signed tx message"
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.SignedTxNack, msg.getBytes)
  }

  def errorBadMessage: Unit = {
    val msg = "Cannot deserialise that message, wrong code for the bytes?"
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.MalformedMessage, msg.getBytes)
  }

  private def working(blockLedgerOpt: Option[Ledger]): Receive = {

    case BlockLedger(coordinator: ActorRef, blockLedger: Ledger) => {
      context.become(working(Some(blockLedger)))
      coordinator ! AcknowledgeNewLedger

    }

    case NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>

      blockLedgerOpt match {
        case Some(blockLedger) => bytes.toSeqSignedTx.ordered foreach { stx =>
          writeStx(blockLedger, stx)
        }
        case None => errorNoLedger
      }


    case NetworkMessage(MessageKeys.SignedTx, bytes) =>
      log.info(s"Got a signed tx ... ")

      bytes.toSignedTxTry match {
        case Success(stx) => blockLedgerOpt match {
          case Some(blockLedger) => writeStx(blockLedger, stx)
          case None => errorNoLedger
        }
        case Failure(e) => errorBadMessage
      }

  }

}
