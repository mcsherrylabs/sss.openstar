package sss.asado.message.serialize

import org.joda.time.LocalDateTime
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.crypto.SeedBytes
import sss.asado.ledger.LedgerItem
import sss.asado.message._
import sss.asado.util.ByteArrayComparisonOps

/**
  * Created by alan on 2/15/16.
  */

class MessageSerializeSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  val le = LedgerItem(3.toByte, SeedBytes(32), SeedBytes(32))

  "An addressed message " should " serialize and deserialize " in {
    val test = AddressedMessage(le, SeedBytes(32))
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageAddressed
    assert(hydrated.ledgerItem == test.ledgerItem)
    assert(hydrated.msg isSame test.msg)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Message Query " should " serialize and deserialize " in {

    val test = MessageQuery(4444, 567)
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageQuery
    assert(hydrated.lastIndex == test.lastIndex)
    assert(hydrated.pageSize == test.pageSize)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Success Message response " should " serialize and deserialize " in {
    val test: MessageResponse = SuccessResponse(SeedBytes(32))
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageResponse
    assert(hydrated.success == test.success)
    assert(hydrated.txId isSame test.txId)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Failure Message response " should " serialize and deserialize " in {
    val test: MessageResponse = FailureResponse(SeedBytes(3), "What the hell happened here?")
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessageResponse
    assert(hydrated.success == test.success)
    assert(hydrated.txId isSame test.txId)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }

  "A Message " should " serialize and deserialize " in {
    val test= Message("from", SeedBytes(34), le.toBytes, 20, LocalDateTime.now())
    val asBytes = test.toBytes
    val hydrated = asBytes.toMessage
    assert(hydrated.createdAt == test.createdAt)
    assert(hydrated.index  == test.index)
    assert(hydrated.from == test.from)
    assert(hydrated.hashCode == test.hashCode)
    assert(hydrated == test)
  }
}
