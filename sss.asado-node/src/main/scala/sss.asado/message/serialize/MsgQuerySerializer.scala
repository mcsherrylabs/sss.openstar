package sss.asado.message.serialize

import sss.asado.message.{Message, MessageQuery}
import sss.asado.util.Serialize._

/**
  * Created by alan on 6/8/16.
  */
object MsgQuerySerializer extends Serializer[MessageQuery]{
  def toBytes(o: MessageQuery): Array[Byte] =
    (LongSerializer(o.lastIndex) ++
      IntSerializer(o.pageSize)).toBytes

  def fromBytes(bs: Array[Byte]): MessageQuery = {
    val extracted = bs.extract(LongDeSerialize, IntDeSerialize)
    MessageQuery(extracted(0)[Long], extracted(1)[Int])
  }

}
