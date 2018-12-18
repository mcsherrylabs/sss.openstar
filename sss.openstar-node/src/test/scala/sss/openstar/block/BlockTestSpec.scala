package sss.openstar.block

import java.sql.SQLException

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.common.block._
import sss.openstar.ledger._
import sss.db.Db
import sss.openstar.account.PrivateKeyAccount

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
      val index = 1
      val block = block999
      assert(block999.entries.size === 0)
      assert(block999.page(1,100) == Seq())
      val writtenIndex = block999.journal(index, dumbSignedTx)
      block999.commit(index)
      assert(writtenIndex === index)
      assert(block999.entries.size === 1)
      assert(block999.page(1,100).size == 1)
      assert(block999.page(1,100).head.swap.getOrElse(Array()) === dumbSignedTx.toBytes, "Signed tx comparison failed?")
      assert(block999.entries === Seq(BlockTx(index, dumbSignedTx)))
      assert(block999.entries === Seq(BlockTx(index, dumbSignedTx)))
      assert(block999.getUnCommitted === Seq())

  }

  it should "allow an uncommitted tx to be committed" in {

    block999.commit(1)
    assert(block999.getUnCommitted.size === 0)

  }

  it should "allow a tx written, and retrieved " in {
    val index = block999.write( dumbSignedTx2 )
    assert(block999(dumbSignedTx2.txId) ===  BlockTx(index, dumbSignedTx2))
    assert(block999.getUnCommitted.size === 0)

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

    val index = block999.write(dumbSignedTx3)

    // After running this test, the next db index is 24!!!
    // WARNING!!!!
    intercept[SQLException] {
      block999.journal(index, dumbSignedTx3)
    }
  }

  //TODO test validate and reject.

}
