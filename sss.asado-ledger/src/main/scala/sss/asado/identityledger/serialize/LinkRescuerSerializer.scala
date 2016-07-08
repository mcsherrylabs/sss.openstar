package sss.asado.identityledger.serialize


import sss.asado.util.Serialize._
import sss.asado.identityledger._

/**
  * Created by alan on 6/1/16.
  */
object LinkRescuerSerializer extends Serializer[LinkRescuer] {


  def toBytes(linkRescuer: LinkRescuer): Array[Byte] = {
    (ByteSerializer(LinkRescuerCode) ++
      LongSerializer(linkRescuer.uniqueMessage) ++
      StringSerializer(linkRescuer.rescuer) ++
      StringSerializer(linkRescuer.identity)
      ).toBytes
  }

  def fromBytes(bytes: Array[Byte]): LinkRescuer = {
    val extracted = bytes.extract(ByteDeSerialize, LongDeSerialize, StringDeSerialize, StringDeSerialize)
    require(extracted(0)[Byte] == LinkRescuerCode, s"Wrong leading byte for Link Rescuer ${bytes.head} instead of $LinkRescuerCode")
    new LinkRescuer(extracted(2)[String], extracted(3)[String]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }
}
