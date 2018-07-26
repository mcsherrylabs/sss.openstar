package sss.ui.nobu

import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.message._
import sss.asado.util.Serialize._
/**
  * Created by alan on 12/13/16.
  */
object IdentityClaimMessagePayload {

  def fromBytes(bytes: Array[Byte]):IdentityClaimMessagePayload = {
    val extracted = bytes.extract(StringDeSerialize, StringDeSerialize, ByteArrayDeSerialize, StringDeSerialize)
    IdentityClaimMessagePayload(extracted(0)[String], extracted(1)[String], extracted(2)[Array[Byte]], extracted(3)[String])
  }
}


case class IdentityClaimMessagePayload(claimedIdentity: String, tag: String, publicKey: PublicKey, supportingText: String) extends TypedMessagePayload {
  override def toMessagePayLoad: MessagePayload = MessagePayload(2.toByte,
    (StringSerializer(claimedIdentity) ++
      StringSerializer(tag) ++
      ByteArraySerializer(publicKey) ++
      StringSerializer(supportingText)).toBytes)
}
