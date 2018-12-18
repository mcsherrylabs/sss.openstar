package sss.openstar.message

import scorex.crypto.signatures.SigningFunctions.{PublicKey, SharedSecret}
import sss.openstar.account.NodeIdentity
import sss.openstar.crypto.{CBCEncryption, SeedBytes}
import sss.openstar.crypto.CBCEncryption.InitVector
import sss.openstar.util.Serialize._

import scala.util.Random

/**
  * Created by alan on 6/28/16.
  */
object MessageEcryption {

  def encryptedMessage(b: Array[Byte]): EncryptedMessage = {
    EncryptedMessage.tupled(
      b.extract(ByteArrayDeSerialize,
        StringDeSerialize(CBCEncryption.initVector)
      )
    )
  }

  private def textWithSecret(b: Array[Byte]): TextWithSecret = {

    TextWithSecret.tupled(
      b.extract(StringDeSerialize,
        ByteArrayDeSerialize
      )
    )
  }

  case class EncryptedMessage(encrypted:Array[Byte], iv: InitVector) extends TypedMessagePayload {

    override lazy val toMessagePayLoad: MessagePayload = MessagePayload(MessagePayloadDecoder.EncryptedMessageType, toBytes)

    lazy val toBytes: Array[Byte] = (ByteArraySerializer(encrypted) ++ StringSerializer(iv.asString)).toBytes

    def decrypt(receiver: NodeIdentity,
                sendPublicKey: PublicKey): TextWithSecret = {
      val sharedSecret: SharedSecret = receiver.createSharedSecret(sendPublicKey)
      val decryptedMessage = CBCEncryption.decrypt(sharedSecret, encrypted, iv)
      textWithSecret(decryptedMessage)
    }
  }

  case class TextWithSecret(text:String, secret : Array[Byte]) {
    lazy val toBytes: Array[Byte] = (StringSerializer(text) ++ ByteArraySerializer(secret)).toBytes
  }

  def encryptWithEmbeddedSecret(sender: NodeIdentity,
                                receiverKey:PublicKey,
                                text: String,
                                secret: Array[Byte]): EncryptedMessage = {

    val sharedSecret: SharedSecret = sender.createSharedSecret(receiverKey)
    val initVector = SeedBytes.secureSeed(16)
    val iv = CBCEncryption.initVector(initVector)
    val bytes: Array[Byte] = TextWithSecret(text, secret).toBytes
    val encryptedMessage = CBCEncryption.encrypt(sharedSecret, bytes, iv)
    EncryptedMessage(encryptedMessage, iv)
  }


}
