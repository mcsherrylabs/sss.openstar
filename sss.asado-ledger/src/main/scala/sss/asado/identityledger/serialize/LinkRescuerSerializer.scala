package sss.asado.identityledger.serialize

import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 6/1/16.
  */
object LinkRescuerSerializer extends Serializer[LinkRescuer] {

  def toBytes(linkRescuer: LinkRescuer): Array[Byte] = {
    (ByteSerializer(LinkRescuerCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.rescuer) ++
      StringSerializer(linkRescuer.identity)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): LinkRescuer = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize,
                                  StringDeSerialize)
    require(
      extracted._1 == LinkRescuerCode,
      s"Wrong leading byte for Link Rescuer ${bytes.head} instead of $LinkRescuerCode")
    new LinkRescuer(extracted._3, extracted._4) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }
}
