package sss.asado.identityledger.serialize


import sss.asado.identityledger._
import sss.asado.util.Serialize._


/**
  * Created by alan on 5/31/16.
  */
object UnLinkByKeySerializer extends Serializer[UnLinkByKey]{
  def toBytes(unLinkiByKey: UnLinkByKey): Array[Byte] = {
    (ByteSerializer(UnLinkByKeyCode) ++
      LongSerializer(unLinkiByKey.uniqueMessage) ++
      StringSerializer(unLinkiByKey.identity) ++
      ByteArraySerializer(unLinkiByKey.pKey)).toBytes
  }

  def fromBytes(bytes: Array[Byte]): UnLinkByKey = {
    val extracted = bytes.extract(ByteDeSerialize, LongDeSerialize, StringDeSerialize, ByteArrayDeSerialize)
    require(extracted(0)[Byte] == UnLinkByKeyCode, s"Wrong leading byte for ${bytes.head} instead of $UnLinkByKeyCode")
    new UnLinkByKey(extracted(2)[String], extracted(3)[Array[Byte]]) {
      private[identityledger] override val uniqueMessage: Long = extracted(1)[Long]
    }
  }

}
