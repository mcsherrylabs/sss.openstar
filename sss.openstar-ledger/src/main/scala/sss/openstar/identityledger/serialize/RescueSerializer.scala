package sss.openstar.identityledger.serialize

import sss.openstar.identityledger._
import sss.openstar.util.Serialize._

/**
  * Created by alan on 6/1/16.
  */
object RescueSerializer extends Serializer[Rescue] {
  def toBytes(rescuer: Rescue): Array[Byte] = {
    (ByteSerializer(RescueCode) ++
      LongSerializer(rescuer.uniqueMessage) ++
      StringSerializer(rescuer.rescuer) ++
      StringSerializer(rescuer.identity) ++
      ByteArraySerializer(rescuer.pKey) ++
      StringSerializer(rescuer.tag)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): Rescue = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize,
                                  StringDeSerialize,
                                  ByteArrayDeSerialize,
                                  StringDeSerialize)

    require(
      extracted._1 == RescueCode,
      s"Wrong leading byte for Rescue ${bytes.head} instead of $RescueCode")

    new Rescue(extracted._3, extracted._4, extracted._5, extracted._6) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }
}
