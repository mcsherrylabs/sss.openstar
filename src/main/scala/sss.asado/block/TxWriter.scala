package sss.asado.block

import akka.actor.Actor
import ledger._
import sss.asado.MessageKeys
import sss.asado.network.NetworkMessage
/**
  * Created by alan on 3/18/16.
  */
class TxWriter extends Actor {
  override def receive: Receive = {
    case NetworkMessage(MessageKeys.SignedTx, bytes) =>
      val signedTx = bytes.toSignedTx

  }
}
