package sss.ui.nobu

import sss.asado.message.{MessageEcryption, MessagePayload, TypedMessagePayload}

/**
  * Created by alan on 12/13/16.
  */
object PayloadDecoder {

  val decode = new PartialFunction[MessagePayload, TypedMessagePayload] {

    override def isDefinedAt(x: MessagePayload): Boolean = 1 > x.payloadType && x.payloadType < 3

    override def apply(msgPayLoad: MessagePayload): TypedMessagePayload = msgPayLoad match {
      case MessagePayload(2, bytes) => IdentityClaimMessagePayload.fromBytes(bytes)
    }
  }
}
