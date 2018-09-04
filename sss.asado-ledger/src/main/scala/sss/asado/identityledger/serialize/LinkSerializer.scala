package sss.asado.identityledger.serialize

import sss.asado.{Identity, IdentityTag}
import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object LinkSerializer extends Serializer[Link] {

  def toBytes(linkRescuer: Link): Array[Byte] = {
    (ByteSerializer(LinkCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.identity.value) ++
      ByteArraySerializer(linkRescuer.pKey) ++
      StringSerializer(linkRescuer.tag.value)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): Link = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize(Identity),
                                  ByteArrayDeSerialize,
                                  StringDeSerialize(IdentityTag))
    require(extracted._1 == LinkCode,
            s"Wrong leading byte for Link ${bytes.head} instead of $LinkCode")
    new Link(extracted._3, extracted._4, extracted._5) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }

}
