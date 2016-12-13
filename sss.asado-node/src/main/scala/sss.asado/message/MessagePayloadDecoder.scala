package sss.asado.message

/**
  * Created by alan on 12/13/16.
  */
object MessagePayloadDecoder {

  val EncryptedMessageType = 1.toByte

  val decode = new PartialFunction[MessagePayload, TypedMessagePayload] {

    override def isDefinedAt(x: MessagePayload): Boolean = 0 > x.payloadType && x.payloadType < 2

    override def apply(msgPayLoad: MessagePayload): TypedMessagePayload = msgPayLoad match {
      case MessagePayload(EncryptedMessageType, bytes) => MessageEcryption.encryptedMessage(bytes)
    }
  }
}
