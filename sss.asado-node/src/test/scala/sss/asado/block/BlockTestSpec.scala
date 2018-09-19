package sss.asado.block

import java.sql.SQLException

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.PrivateKeyAccount
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block._
import sss.asado.ledger._
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */

object BlockTestSpec {
  lazy val pkPair = PrivateKeyAccount(DummySeedBytes)

  implicit val chainId: GlobalChainIdMask = 4.toByte
  implicit val db = Db()
  val someBlock = Block(99)
  val block999 = Block(9999)

  lazy val dumbSignedTx = LedgerItem(99, DummySeedBytes(32), DummySeedBytes(12))

  lazy val dumbSignedTx2 = LedgerItem(99, DummySeedBytes(32), DummySeedBytes(12))

  lazy val dumbSignedTx3 = LedgerItem(99, DummySeedBytes(32), DummySeedBytes(12))

}
class BlockTestSpec extends FlatSpec with Matchers {

  import BlockTestSpec._

  "A block " should "start off empty" in {
    assert(block999.entries.length == 0)
  }

  it should "allow an uncommitted tx to be written " in {
      val index = 0
      val block = block999
      assert(block999.entries.size === 0)
      assert(block999.page(0,100) == Seq())
      val writtenIndex = block999.journal(index, dumbSignedTx)
      assert(writtenIndex === index)
      assert(block999.entries.size === 1)
      assert(block999.page(0,100).size == 1)
      assert(block999.page(0,100).head === dumbSignedTx.toBytes, "Signed tx comparison failed?")
      assert(block999.entries === Seq(BlockTx(index, dumbSignedTx)))
      assert(block999.entries === Seq(BlockTx(index, dumbSignedTx)))
      assert(block999.getUnCommitted === Seq(BlockTx(index, dumbSignedTx)))
      assert(block999.getUnconfirmed(1) === Seq((0, BlockTx(index, dumbSignedTx))))
      assert(block999.maxMonotonicCommittedIndex === -1)
  }

  it should "allow an uncommitted tx to be committed" in {

    block999.commit(0)
    assert(block999.maxMonotonicCommittedIndex === 0)
    assert(block999.getUnCommitted.size === 0)
    assert(block999.getUnconfirmed(1) === Seq((0, BlockTx(0, dumbSignedTx))))
  }

  it should "allow an unconfirmed tx to be confirmed" in {

    block999.confirm(BlockTxId(dumbSignedTx.txId, 0))
    assert(block999.getUnCommitted.size === 0)
    assert(block999.getUnconfirmed(1).size === 0)
    assert(block999.getUnconfirmed(2) === Seq((1, BlockTx(0, dumbSignedTx))))

    block999.confirm(BlockTxId(dumbSignedTx.txId, 0))
    assert(block999.getUnCommitted.size === 0)

    assert(block999.getUnconfirmed(2).size === 0)

  }


  it should "allow a tx written, retrieved and confirmed " in {
    val index = block999.write( dumbSignedTx2 )
    assert(block999(dumbSignedTx2.txId) ===  BlockTx(index, dumbSignedTx2))
    assert(block999.getUnCommitted.size === 0)
    assert(block999.getUnconfirmed(1).size === 1)

    block999.confirm(BlockTxId(dumbSignedTx2.txId, index))
    assert(block999.getUnconfirmed(1).size === 0)
    assert(block999.maxMonotonicCommittedIndex === 1)

  }

  "Max monotonic index " should " ignore a gap in indexes" in {

    val writtenIndex = block999.journal(9999, dumbSignedTx3)
    assert(block999.maxMonotonicCommittedIndex === 1)

  }

  it should "not retrieve non existent txid " in {
    assert(block999.get(DummySeedBytes(TxIdLen)).isEmpty)
  }


  it should " allow standard ledger entries to be persisted " in {

    val stx = LedgerItem(99, DummySeedBytes(32), DummySeedBytes(12))

    intercept[NoSuchElementException] {
      someBlock(stx.txId)
    }

    someBlock.write(stx)
    val retrieved = someBlock(stx.txId)
    assert(retrieved.ledgerItem == stx)
  }


  it should "prevent the same tx being written a second time  " in {
    intercept[SQLException] {
      block999.write(dumbSignedTx)
    }

    // After running this test, the next db index is 24!!!
    // WARNING!!!!
    intercept[SQLException] {
      block999.journal(23, dumbSignedTx)
    }
  }


  "FindSmallest " should "find the smallest missing  " in {

    var seq = Seq[Long]()
    assert(Block.findSmallestMissing(seq) == -1)
    seq = Seq[Long](0)
    assert(Block.findSmallestMissing(seq) == 0)
    seq = Seq[Long](1,2)
    assert(Block.findSmallestMissing(seq) == -1)
    seq = Seq[Long](0,1,2)
    assert(Block.findSmallestMissing(seq) == 2)
    seq = Seq[Long](0, 1,2, 4, 5, 6)
    assert(Block.findSmallestMissing(seq) == 2)
    seq = Seq[Long](0, 1,2, 3, 4, 6)
    assert(Block.findSmallestMissing(seq) == 4)
  }
}
