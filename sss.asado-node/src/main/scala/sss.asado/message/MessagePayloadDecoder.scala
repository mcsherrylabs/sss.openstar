package sss.asado.message

/**
  * Created by alan on 12/13/16.
  */
object MessagePayloadDecoder {

  val EncryptedMessageType = 1.toByte
  val ChessMessageType     = 2.toByte

  val decode = new PartialFunction[MessagePayload, TypedMessagePayload] {

    override def isDefinedAt(x: MessagePayload): Boolean = 0 < x.payloadType && 2 > x.payloadType

    override def apply(msgPayLoad: MessagePayload): TypedMessagePayload = msgPayLoad match {
      case MessagePayload(1, bytes) => MessageEcryption.encryptedMessage(bytes)
      case MessagePayload(2, bytes) => ??? // Put chess board renderer here ...
    }
  }
}
