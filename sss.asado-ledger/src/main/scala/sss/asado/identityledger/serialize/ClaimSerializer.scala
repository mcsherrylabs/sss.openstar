package sss.asado.identityledger.serialize


import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object ClaimSerializer extends Serializer[Claim] {
  def toBytes(claim: Claim): Array[Byte] = {
    (ByteSerializer(ClaimCode) ++
      LongSerializer(claim.uniqueMessage) ++
      StringSerializer(claim.identity) ++
      ByteArraySerializer(claim.pKey)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): Claim = {
    val extracted = bytes.extract(ByteDeSerialize, LongDeSerialize, StringDeSerialize, ByteArrayDeSerialize)
    require(extracted(0)[Byte] == ClaimCode, s"Wrong leading byte for Claim ${bytes.head} instead of $ClaimCode")
    new Claim(extracted(2)[String], extracted(3)[Array[Byte]]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }

}
