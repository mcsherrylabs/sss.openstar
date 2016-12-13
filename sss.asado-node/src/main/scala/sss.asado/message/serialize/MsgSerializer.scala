package sss.asado.message.serialize

import org.joda.time.LocalDateTime
import sss.asado.message._
import sss.asado.util.Serialize._

/**
  * Created by alan on 6/8/16.
  */
object MsgSerializer extends Serializer[Message] {

  def toBytes(o: Message): Array[Byte] =
    (StringSerializer(o.from) ++
      ByteArraySerializer(o.msgPayload.toBytes) ++
      ByteArraySerializer(o.tx) ++
      LongSerializer(o.index) ++
      LongSerializer(o.createdAt.toDate.getTime)).toBytes

  def fromBytes(bs: Array[Byte]): Message = {
    val extracted = bs.extract(
      StringDeSerialize,
      ByteArrayDeSerialize,
      ByteArrayDeSerialize ,
      LongDeSerialize,
      LongDeSerialize)

    Message(extracted(0)[String],
      extracted(1)[Array[Byte]].toMessagePayload,
      extracted(2)[Array[Byte]],
      extracted(3)[Long],
      new LocalDateTime(extracted(4)[Long]))
  }

}
