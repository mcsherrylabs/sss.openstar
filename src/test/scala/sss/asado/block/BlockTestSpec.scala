package sss.asado.block

import java.sql.SQLException

import block.{BlockTx, BlockTxId}
import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.util.SeedBytes
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */

object BlockTestSpec {
  lazy val pkPair = PrivateKeyAccount()

  implicit val db = Db()
  val someBlock = Block(99)
  val block999 = Block(999)
  lazy val genisis = SignedTx((GenisesTx(outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey))))))
  lazy val createGenesis = {
    someBlock.write(genisis)
    genisis
  }
  lazy val dumbSignedTx = {
    val ins = Seq(TxInput(TxIndex(SeedBytes(TxIdLen), 0), 100, PrivateKeySig))
    val outs = Seq(TxOutput(99, SinglePrivateKey(pkPair.publicKey)), TxOutput(1, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    SignedTx(tx)
  }

  lazy val dumbSignedTx2 = {
    val ins = Seq(TxInput(TxIndex(SeedBytes(TxIdLen), 0), 100, PrivateKeySig))
    val outs = Seq(TxOutput(99, SinglePrivateKey(pkPair.publicKey)), TxOutput(1, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    SignedTx(tx)
  }

  lazy val dumbSignedTx3 = {
    val ins = Seq(TxInput(TxIndex(SeedBytes(TxIdLen), 0), 100, PrivateKeySig))
    val outs = Seq(TxOutput(99, SinglePrivateKey(pkPair.publicKey)), TxOutput(1, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    SignedTx(tx)
  }

  def createSignedTx(inputTx : SignedTx) = {
    val ins = Seq(TxInput(TxIndex(inputTx.tx.txId, 0), 100, PrivateKeySig))
    val outs = Seq(TxOutput(99, SinglePrivateKey(pkPair.publicKey)), TxOutput(1, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    SignedTx(tx)
  }
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
    assert(block999.get(SeedBytes(TxIdLen)).isEmpty)
  }

  "Tx DB storage " should " allow gensies ledger entries to be persisted " in {

    createGenesis
    val retrieved = someBlock(genisis.tx.txId)
    assert(retrieved.signedTx == genisis)
  }

  it should " allow standard ledger entries to be persisted " in {

    val inputTx = someBlock(genisis.tx.txId)
    val stx = createSignedTx (inputTx.signedTx)

    intercept[NoSuchElementException] {
      someBlock(stx.txId)
    }

    someBlock.write(stx)
    val retrieved = someBlock(stx.txId)
    assert(retrieved.signedTx == stx)
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
