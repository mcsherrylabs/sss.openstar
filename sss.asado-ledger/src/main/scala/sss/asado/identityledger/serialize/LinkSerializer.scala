package sss.asado.identityledger.serialize

import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object LinkSerializer extends Serializer[Link]{

  def toBytes(linkRescuer: Link): Array[Byte] = {
    (ByteSerializer(LinkCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.identity) ++
      ByteArraySerializer(linkRescuer.pKey) ++
      StringSerializer(linkRescuer.tag)
      ).toBytes
  }

  def fromBytes(bytes: Array[Byte]): Link = {
    val extracted = bytes.extract(ByteDeSerialize, LongDeSerialize, StringDeSerialize, ByteArrayDeSerialize, StringDeSerialize)
    require(extracted(0)[Byte] == LinkCode, s"Wrong leading byte for Link ${bytes.head} instead of $LinkCode")
    new Link(extracted(2)[String], extracted(3)[Array[Byte]], extracted(4)[String]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }

}
