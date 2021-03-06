package sss.openstar.identityledger.serialize

import sss.openstar.identityledger._
import sss.openstar.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object LinkSerializer extends Serializer[Link] {

  def toBytes(linkRescuer: Link): Array[Byte] = {
    (ByteSerializer(LinkCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.identity) ++
      ByteArraySerializer(linkRescuer.pKey) ++
      StringSerializer(linkRescuer.tag)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): Link = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize,
                                  ByteArrayDeSerialize,
                                  StringDeSerialize)
    require(extracted._1 == LinkCode,
            s"Wrong leading byte for Link ${bytes.head} instead of $LinkCode")
    new Link(extracted._3, extracted._4, extracted._5) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }

}
