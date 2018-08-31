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
    val extracted = b.extract(ByteDeSerialize,
                              LongDeSerialize,
                              StringDeSerialize,
                              StringDeSerialize)
    require(extracted._1 == UnLinkRescuerCode,
            s"Wrong leading Byte ${extracted._1} should be $UnLinkRescuerCode")
    new UnLinkRescuer(extracted._3, extracted._4) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }
}
