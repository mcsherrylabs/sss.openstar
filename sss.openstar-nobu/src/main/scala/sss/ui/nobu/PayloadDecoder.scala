package sss.ui.nobu

import sss.openstar.message.{MessageEcryption, MessagePayload, TypedMessagePayload}

/**
  * Created by alan on 12/13/16.
  */
object PayloadDecoder {

  val decode = new PartialFunction[MessagePayload, TypedMessagePayload] {

    override def isDefinedAt(x: MessagePayload): Boolean = 1 < x.payloadType && 3 > x.payloadType

    override def apply(msgPayLoad: MessagePayload): TypedMessagePayload = msgPayLoad match {
      case MessagePayload(2, bytes) => IdentityClaimMessagePayload.fromBytes(bytes)
    }
  }
}
