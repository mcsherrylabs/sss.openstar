package sss.asado.message.serialize

import sss.asado.message.MessageQuery
import sss.asado.util.Serialize._

/**
  * Created by alan on 6/8/16.
  */
object MsgQuerySerializer extends Serializer[MessageQuery]{
  def toBytes(o: MessageQuery): Array[Byte] =
    (StringSerializer(o.who) ++
      LongSerializer(o.lastIndex) ++
      IntSerializer(o.pageSize)).toBytes

  def fromBytes(bs: Array[Byte]): MessageQuery = {

    MessageQuery.tupled(
      bs.extract(
        StringDeSerialize,
        LongDeSerialize,
        IntDeSerialize
      )
    )
  }

}
