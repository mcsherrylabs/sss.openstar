package sss.openstar.message.serialize

import sss.openstar.message.{FailureResponse, MessageResponse, SuccessResponse}
import sss.openstar.util.Serialize._

/**
  * Created by alan on 6/8/16.
  */
object MsgResponseSerializer extends Serializer[MessageResponse] {

  def toBytes(o: MessageResponse): Array[Byte] = {
    o match {
      case SuccessResponse(responseId) => (BooleanSerializer(true) ++ ByteArraySerializer(o.txId)).toBytes
      case FailureResponse(responseId, info) => (BooleanSerializer(false) ++
        ByteArraySerializer(o.txId) ++
        StringSerializer(info)).toBytes
    }
  }

  def fromBytes(bs: Array[Byte]): MessageResponse = {

    bs.extract(BooleanDeSerialize, ByteArrayRawDeSerialize) match {
      case (true, rest) =>
        SuccessResponse(rest.extract(ByteArrayDeSerialize))
      case (_, rest) =>
        FailureResponse.tupled(rest.extract(ByteArrayDeSerialize, StringDeSerialize))
    }
  }

}
