package sss.asado

import org.joda.time.LocalDateTime
import sss.asado.ledger.LedgerItem
import sss.asado.message.serialize.{AddressedMessageSerializer, MsgQuerySerializer, MsgResponseSerializer, MsgSerializer}
import sss.asado.util.Serialize.ToBytes
import sss.asado.ledger._
import sss.asado.balanceledger._
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

  case object EndMessagePage
  case object EndMessageQuery

  type Identity = String

  case class SavedAddressedMessage(to: Identity, index: Long, savedAt: LocalDateTime, addrMsg: AddressedMessage) {
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: SavedAddressedMessage =>
          (that.addrMsg == addrMsg) &&
            (that.index == index) &&
            (that.to == to)
        case _ => false
      }
    }

    override def hashCode(): Int = addrMsg.hashCode() + to.hashCode + index.hashCode()
  }

  case class AddressedMessage(ledgerItem: LedgerItem, msg : Array[Byte]) {
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case that: AddressedMessage =>
          (that.ledgerItem == ledgerItem) &&
            (that.msg isSame msg)
        case _ => false
      }
    }

    override def hashCode(): Int = ledgerItem.hashCode() + msg.hash
  }

  case class Message(from: Identity,
                     msg: Array[Byte],
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
            (that.msg isSame msg)
        case _ => false
      }
    }

    override def hashCode(): Int = index.hashCode() + createdAt.hashCode + msg.hash + tx.hash

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
