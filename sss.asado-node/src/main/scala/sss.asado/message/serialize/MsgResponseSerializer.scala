package sss.asado.message.serialize

import sss.asado.message.{FailureResponse, MessageResponse, SuccessResponse}
import sss.asado.util.Serialize._

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
    val isSuccess = bs.extract(BooleanDeSerialize)
    isSuccess(0)[Boolean] match {
      case true =>
        val extracted = bs.extract(BooleanDeSerialize, ByteArrayDeSerialize)
        SuccessResponse(extracted(1)[Array[Byte]])
      case false =>
        val extracted = bs.extract(BooleanDeSerialize, ByteArrayDeSerialize, StringDeSerialize)
        FailureResponse(extracted(1)[Array[Byte]], extracted(2)[String])
    }
  }

}
