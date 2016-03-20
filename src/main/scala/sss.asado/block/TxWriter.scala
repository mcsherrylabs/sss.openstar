package sss.asado.block

import akka.actor.Actor
import com.google.common.primitives.Longs
import ledger._
import sss.asado.MessageKeys
import sss.asado.ledger.Ledger
import sss.asado.network.NetworkMessage

import scala.util.{Failure, Success}
/**
  * Created by alan on 3/18/16.
  */
class TxWriter extends Actor {
  override def receive: Receive = init

  private def working(blockLedger: Ledger): Receive = {
    case NetworkMessage(MessageKeys.SignedTx, bytes) =>
      val signedTx = bytes.toSignedTx

      blockLedger.write(signedTx) match {
        case Success(height: Long) => sender() !  NetworkMessage(MessageKeys.SignedTxAck, Longs.toByteArray(height))
        case Failure(e) => sender() !  NetworkMessage(MessageKeys.SignedTxNack, e.getMessage.getBytes)
      }

  }

  private def init: Receive = {

    case blockLedger: Ledger => context.become(working(blockLedger))
  }
}
