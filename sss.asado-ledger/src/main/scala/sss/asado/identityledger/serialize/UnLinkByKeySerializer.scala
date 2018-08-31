package sss.asado.identityledger.serialize

import sss.asado.identityledger._
import sss.asado.util.Serialize._

/**
  * Created by alan on 5/31/16.
  */
object UnLinkByKeySerializer extends Serializer[UnLinkByKey] {
  def toBytes(unLinkiByKey: UnLinkByKey): Array[Byte] = {
    (ByteSerializer(UnLinkByKeyCode) ++
      LongSerializer(unLinkiByKey.uniqueMessage) ++
      StringSerializer(unLinkiByKey.identity) ++
      ByteArraySerializer(unLinkiByKey.pKey)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): UnLinkByKey = {
    val extracted = bytes.extract(ByteDeSerialize,
                                  LongDeSerialize,
                                  StringDeSerialize,
                                  ByteArrayDeSerialize)
    require(extracted._1 == UnLinkByKeyCode,
            s"Wrong leading byte for ${bytes.head} instead of $UnLinkByKeyCode")
    new UnLinkByKey(extracted._3, extracted._4) {
      private[identityledger] override val uniqueMessage: Long = extracted._2
    }
  }

}
