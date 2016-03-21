package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.google.common.primitives.Longs
import ledger._
import sss.asado.MessageKeys
import sss.asado.ledger.Ledger
import sss.asado.network.NetworkMessage

import scala.util.{Failure, Success}
/**
  * Created by alan on 3/18/16.
  */

class TxWriter extends Actor with ActorLogging {
  override def receive: Receive = working(None)

  private def working(blockLedgerOpt: Option[Ledger]): Receive = {

    case BlockLedger(coordinator: ActorRef, blockLedger: Ledger) => {
      context.become(working(Some(blockLedger)))
      coordinator ! AcknowledgeNewLedger

    }

    case NetworkMessage(MessageKeys.SignedTx, bytes) =>
      val signedTx = bytes.toSignedTx

      blockLedgerOpt match {
        case Some(blockLedger) => blockLedger(signedTx) match {
          case Success(height) => sender() ! NetworkMessage(MessageKeys.SignedTxAck, Longs.toByteArray(height))
          case Failure(e) => sender() ! NetworkMessage(MessageKeys.SignedTxNack, e.getMessage.getBytes)
        }
        case None =>
          val msg = "No ledger in play, cannnot handle signed tx message"
          log.error(msg)
          sender() ! NetworkMessage(MessageKeys.SignedTxNack, msg.getBytes)
      }

  }

}
