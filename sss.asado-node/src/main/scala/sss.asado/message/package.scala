package sss.asado

import org.joda.time.LocalDateTime
import sss.asado.balanceledger._
import sss.asado.ledger.{LedgerItem, _}
import sss.asado.message.serialize._
import sss.asado.util.Serialize.ToBytes
/**
  * Created by alan on 6/6/16.
  */
package object message {

  case class MessageQuery(lastIndex: Long, pageSize: Int)

  trait MessageResponse {
    val success: Boolean
    val txId: TxId

    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: MessageResponse =>
          (that.success == success) && (that.txId isSame txId)
        case _ => false
      }
    }

    override def hashCode(): Int = success.hashCode() + txId.hash
  }
  case class SuccessResponse(txId: TxId) extends MessageResponse {
    val success = true
  }

  case class FailureResponse(txId: TxId, info: String) extends MessageResponse {
    val success = false
  }

  trait TypedMessagePayload {
    def toMessagePayLoad: MessagePayload
  }

  case object EndMessagePage extends ToBytes {
    override def toBytes: Array[Byte] = Array.emptyByteArray
  }

  case object EndMessageQuery extends ToBytes {
    override def toBytes: Array[Byte] = Array.emptyByteArray
  }

  type Identity = String

  case class SavedAddressedMessage(to: Identity, index: Long, savedAt: LocalDateTime, addrMsg: AddressedMessage)
  case class AddressedMessage(ledgerItem: LedgerItem, msgPayload : MessagePayload)

  case class MessagePayload(payloadType: Byte, payload: Array[Byte]) {
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: MessagePayload =>
          (that.payloadType == payloadType) && (that.payload isSame payload)
        case _ => false
      }
    }

    override def hashCode(): Int = payloadType.hashCode() + payload.hash
  }

  case class Message(from: Identity,
                     msgPayload: MessagePayload,
                     tx: Array[Byte],
                     index: Long,
                     createdAt: LocalDateTime) {

    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: Message =>
          (that.index == index) &&
            (that.createdAt == createdAt) &&
            (that.from == from) &&
            (that.tx isSame tx) &&
            (that.msgPayload == msgPayload)
        case _ => false
      }
    }

    override def hashCode(): Int = index.hashCode() + createdAt.hashCode + msgPayload.hashCode + tx.hash

  }


  implicit class ToMsgPayload(bs: Array[Byte]) {
    def toMessagePayload: MessagePayload = MsgPayloadSerializer.fromBytes(bs)
  }

  implicit class MsgPayloadToBytes(o : MessagePayload) extends ToBytes {
    override def toBytes: Array[Byte] = MsgPayloadSerializer.toBytes(o)
  }

  implicit class ToMsgResponse(bs: Array[Byte]) {
    def toMessageResponse: MessageResponse = MsgResponseSerializer.fromBytes(bs)
  }

  implicit class MsgResponseToBytes(o : MessageResponse) extends ToBytes {
    override def toBytes: Array[Byte] = MsgResponseSerializer.toBytes(o)
  }

  implicit class ToMsg(bs: Array[Byte]) {
    def toMessage: Message = MsgSerializer.fromBytes(bs)
  }

  implicit class MsgToBytes(o: Message) extends ToBytes {
    override def toBytes: Array[Byte] =  MsgSerializer.toBytes(o)
  }

  implicit class ToMsgQuery(bs: Array[Byte]) {
    def toMessageQuery: MessageQuery = MsgQuerySerializer.fromBytes(bs)
  }

  implicit class MsgQueryToBytes(o: MessageQuery) extends ToBytes {
    override def toBytes: Array[Byte] =  MsgQuerySerializer.toBytes(o)
  }

  implicit class AddrMsgFromBytes(bs: Array[Byte]) {
    def toMessageAddressed: AddressedMessage = AddressedMessageSerializer.fromBytes(bs)
  }

  implicit class AddrMsgToBytes(o: AddressedMessage) extends ToBytes {
    override def toBytes: Array[Byte] = AddressedMessageSerializer.toBytes(o)
  }

}
