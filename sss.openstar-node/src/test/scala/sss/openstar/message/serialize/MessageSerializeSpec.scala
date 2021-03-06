package sss.openstar.message.serialize

import org.joda.time.LocalDateTime
import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.ledger.LedgerItem
import sss.openstar.message._
import sss.openstar.util.ByteArrayComparisonOps

/**
  * Created by alan on 2/15/16.
  */

class MessageSerializeSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  val le = LedgerItem(3.toByte, DummySeedBytes(32), DummySeedBytes(32))

  "A message payload " should " serialize and deserialize " in {
    val test = MessagePayload(2.toByte, DummySeedBytes(32))
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessagePayload
    assert(hydrated.payloadType == test.payloadType)
    assert(hydrated.payload isSame test.payload)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "An addressed message " should " serialize and deserialize " in {
    val test = AddressedMessage("from", le, MessagePayload(2.toByte, DummySeedBytes(12)))
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageAddressed
    assert(hydrated.ledgerItem == test.ledgerItem)
    assert(hydrated.msgPayload == test.msgPayload)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Message Query " should " serialize and deserialize " in {

    val test = MessageQuery("from", 4444, 567)
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageQuery
    assert(hydrated.lastIndex == test.lastIndex)
    assert(hydrated.pageSize == test.pageSize)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Success Message response " should " serialize and deserialize " in {
    val test: MessageResponse = SuccessResponse(DummySeedBytes(32))
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageResponse
    assert(hydrated.success == test.success)
    assert(hydrated.txId isSame test.txId)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Failure Message response " should " serialize and deserialize " in {
    val test: MessageResponse = FailureResponse(DummySeedBytes(3), "What the hell happened here?")
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageResponse
    assert(hydrated.success == test.success)
    assert(hydrated.txId isSame test.txId)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Message " should " serialize and deserialize " in {
    val test= Message("to", "from", MessagePayload(4.toByte, DummySeedBytes(34)), le.toBytes, 20, LocalDateTime.now())
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessage
    assert(hydrated.createdAt == test.createdAt)
    assert(hydrated.index  == test.index)
    assert(hydrated.from == test.from)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }
}
