package sss.asado.identityledger.serialize

import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 6/1/16.
  */
object UnLinkRescuerSerializer extends Serializer[UnLinkRescuer] {

  override def toBytes(t: UnLinkRescuer): Array[Byte] = {
    (ByteSerializer(UnLinkRescuerCode) ++
      LongSerializer(t.uniqueMessage) ++
      StringSerializer(t.rescuer) ++
      StringSerializer(t.identity)).toBytes
  }

  override def fromBytes(b: Array[Byte]): UnLinkRescuer = {
    val extracted = b.extract(ByteDeSerialize, LongDeSerialize,
      StringDeSerialize, StringDeSerialize)
    require(extracted(0)[Byte] == UnLinkRescuerCode,
      s"Wrong leading Byte ${extracted(0)[Byte]} should be $UnLinkRescuerCode")
    new UnLinkRescuer(extracted(2)[String], extracted(3)[String]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }
}
