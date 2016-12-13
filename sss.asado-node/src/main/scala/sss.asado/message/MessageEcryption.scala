package sss.asado.message

import scorex.crypto.signatures.SigningFunctions.{PublicKey, SharedSecret}
import sss.asado.account.NodeIdentity
import sss.asado.crypto.CBCEncryption
import sss.asado.crypto.CBCEncryption.InitVector
import sss.asado.util.Serialize._

import scala.util.Random

/**
  * Created by alan on 6/28/16.
  */
object MessageEcryption {

  def encryptedMessage(b: Array[Byte]): EncryptedMessage = {
    val extracted = b.extract(ByteArrayDeSerialize, StringDeSerialize)
    EncryptedMessage(extracted(0)[Array[Byte]], CBCEncryption.initVector(extracted(1)[String]))
  }

  private def textWithSecret(b: Array[Byte]): TextWithSecret = {
    val extracted = b.extract(StringDeSerialize, ByteArrayDeSerialize)
    TextWithSecret(extracted(0)[String], extracted(1)[Array[Byte]])
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
    val initVector = new Array[Byte](16)
    Random.nextBytes(initVector)
    val iv = CBCEncryption.initVector(initVector)
    val bytes: Array[Byte] = TextWithSecret(text, secret).toBytes
    val encryptedMessage = CBCEncryption.encrypt(sharedSecret, bytes, iv)
    EncryptedMessage(encryptedMessage, iv)
  }


}
