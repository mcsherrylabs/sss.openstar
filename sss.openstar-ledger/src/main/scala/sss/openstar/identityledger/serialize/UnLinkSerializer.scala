package sss.openstar.identityledger.serialize

import sss.openstar.identityledger._
import sss.openstar.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object UnLinkSerializer extends Serializer[UnLink] {
  def toBytes(linkRescuer: UnLink): Array[Byte] = {
    (ByteSerializer(UnLinkCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.identity) ++
      StringSerializer(linkRescuer.tag)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): UnLink = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize,
                                  StringDeSerialize)
    require(
      extracted._1 == UnLinkCode,
      s"Wrong leading byte for unlink ${bytes.head} instead of $UnLinkCode")
    new UnLink(extracted._3, extracted._4) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }

}
