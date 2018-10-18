package sss.asado.block.serialize

import sss.asado.block._
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.common.block._
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.DummySeedBytes
import sss.asado.ledger.LedgerItem

import scala.util.Random

/**
  * Created by alan on 2/15/16.
  */
class BlockSerializerTest extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount(DummySeedBytes(32))

  val height = 33
  val id = 20000
  val stx = LedgerItem(1, DummySeedBytes(32), DummySeedBytes(100))

  "A Find Leader " should " be corrrectly serialised and deserialized " in {
    val c = FindLeader(1234, 99, 98, 4, "Holy Karelia!")
    val asBytes = c.toBytes
    val backAgain = asBytes.toFindLeader
    assert(backAgain.height === c.height)
    assert(backAgain.nodeId === c.nodeId)
    assert(backAgain === c)
  }

  "A Leader " should " be corrrectly serialised and deserialized " in {
    val c = Leader("Holy Karelia!")
    val asBytes = c.toBytes
    val backAgain = asBytes.toLeader
    assert(backAgain.nodeId === c.nodeId)
    assert(backAgain === c)
  }

  "A Vote Leader " should " be corrrectly serialised and deserialized " in {
    val c = VoteLeader("Holy Karelia!", 89, 9)
    val asBytes = c.toBytes
    val backAgain = asBytes.toVoteLeader
    assert(backAgain.nodeId === c.nodeId)
    assert(backAgain === c)
  }

  "A Get Page Tx" should " be corrrectly serialised and deserialized " in {
    val c = GetTxPage(Long.MaxValue, 4, 45)
    val asBytes = c.toBytes
    val backAgain: GetTxPage = asBytes.toGetTxPage
    assert(backAgain.pageSize === c.pageSize)
    assert(backAgain.index === c.index)
    assert(backAgain.blockHeight === c.blockHeight)
    assert(backAgain === c)
  }

  "A block signature " should " be corrrectly serialised and deserialized " in {
    val pk = DummySeedBytes(Random.nextInt(200))
    val sig = DummySeedBytes(Random.nextInt(200))
    val sig2 = DummySeedBytes(Random.nextInt(200))
    val c = BlockSignature(23, new DateTime(), 45, "mycrazynode", pk, sig)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockSignature
    assert(backAgain == c)
    assert(backAgain.index == c.index)
    assert(backAgain.savedAt == c.savedAt)
    assert(backAgain.hashCode() === c.hashCode())
    val c2 = BlockSignature(23, new DateTime(), 45, "mycrazynode", pk, sig2)
    assert(c !== c2)
    assert(c.hashCode() !== c2.hashCode())
    assert(backAgain !== c2)
  }

  "A block signature close block " should " be corrrectly serialised and deserialized " in {

    val sig2 = DummySeedBytes(Random.nextInt(200))
    val allSigs = (0 to 10) map { i =>
      val pk = DummySeedBytes(Random.nextInt(200))
      val sig = DummySeedBytes(Random.nextInt(200))
      BlockSignature(i, new DateTime(), 45 * i, s"${i}mycrazynode", pk, sig)
    }
    val bId = BlockId(222, 3433)

    val distClose = DistributeClose(allSigs, bId)
    val asBytes = distClose.toBytes
    val backAgain = asBytes.toDistributeClose
    assert(distClose.blockSigs.size === backAgain.blockSigs.size)
    assert(distClose.blockId === backAgain.blockId)
    assert(distClose === backAgain)
  }
}
