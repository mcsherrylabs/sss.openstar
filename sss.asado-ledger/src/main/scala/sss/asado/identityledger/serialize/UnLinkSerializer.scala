package sss.asado.identityledger.serialize


import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object UnLinkSerializer extends Serializer[UnLink] {
  def toBytes(linkRescuer: UnLink): Array[Byte] = {
    (ByteSerializer(UnLinkCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.identity) ++
      StringSerializer(linkRescuer.tag)
      ).toBytes
  }

  def fromBytes(bytes: Array[Byte]): UnLink = {
    val extracted = bytes.extract(ByteDeSerialize, LongDeSerialize, StringDeSerialize, StringDeSerialize)
    require(extracted(0)[Byte] == UnLinkCode, s"Wrong leading byte for unlink ${bytes.head} instead of $UnLinkCode")
    new UnLink(extracted(2)[String], extracted(3)[String]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }

}
