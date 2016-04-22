package sss.asado.block

import block.{BlockId, BlockTx}
import contract.{NullDecumbrance, NullEncumbrance}
import ledger.{GenisesTx, SignedTx, StandardTx, TxIndex, TxInput, TxOutput}
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.ledger.Ledger
import sss.asado.util.SeedBytes
import sss.db.Db

import scala.util.{Failure, Try}

/**
  * Created by alan on 2/15/16.
  */
class BlockChainLedgerTest extends FlatSpec with Matchers {


  implicit val db: Db = Db("DBStorageTest")
  def resetUtxo = db.executeSql("TRUNCATE TABLE utxo")

  resetUtxo

  val genesisTx = GenisesTx(outs = Seq(TxOutput(100, NullEncumbrance)))
  val genisis = SignedTx(genesisTx)
  val utxostorage = Ledger()
  val ledger = BlockChainLedger(1)
  ledger.genesis(genesisTx)

  /*
  prevent negative
  prevent out > in
  prevent badly decumbred txs
   */

  "A Ledger " should " prevent txs with bad balances " in {

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 1000,  NullDecumbrance))
    val outs = Seq(TxOutput(1, NullEncumbrance), TxOutput(1, NullEncumbrance))

    expectIllegalArgument(ledger.apply(SignedTx(StandardTx(ins, outs))),
      "Allowed to spend more than there?")

  }

  def expectIllegalArgument(result: => Any, msg:String = "Something wrong"): Unit = {
    Try(result) match {
      case Failure(e: IllegalArgumentException) =>
      case x => fail(msg)
    }
  }

  it should " genesis to be applied twice " in {
    ledger.genesis(genesisTx)
  }

  it should " prevent txs with no inputs " in {

    val ins: Seq[TxInput] = Seq()
    val outs: Seq[TxOutput] = Seq()

    expectIllegalArgument(ledger.apply(SignedTx(StandardTx(ins, outs))),
      "Dont need ins and outs?")

  }

  it should " not allow out balance to be greater than in " in {

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(11, NullEncumbrance))
    expectIllegalArgument(ledger.apply(SignedTx(StandardTx(ins, outs))), "out bigger? ")

  }

  it should " prevent double spend " in {

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val le = ledger.apply(SignedTx(StandardTx(ins, outs)))

    expectIllegalArgument(ledger.apply(SignedTx(StandardTx(ins, outs))))

  }

  it should " prevent spend from invalid tx in" in {

    val ins = Seq(TxInput(TxIndex(SeedBytes(3), 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    expectIllegalArgument( ledger.apply(SignedTx(StandardTx(ins, outs))))

  }

  it should " allow spending from a tx out that was also a tx " in {

    resetUtxo
    val ledger = BlockChainLedger(2)
    val r = ledger.genesis(genesisTx)
    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100, NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val stx = SignedTx(StandardTx(ins, outs))
    ledger(stx)
    val nextIns = Seq(TxInput(TxIndex(stx.tx.txId, 1), 1, NullDecumbrance))
    val nextOuts = Seq(TxOutput(1, NullEncumbrance))
    val nextTx = SignedTx(StandardTx(nextIns, nextOuts))
    ledger(nextTx)

  }

  def resetUTXOBlockAndCreateTx(height: Long): SignedTx = {
    resetUtxo
    Block(height).truncate
    val ledger = BlockChainLedger(height)
    val r = ledger.genesis(genesisTx)
    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100, NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    SignedTx(StandardTx(ins, outs))
  }

  it should "allow journaling of a tx " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    assert(blkChnTx.blockTx.index === 34)
    assert(blkChnTx.blockTx.signedTx === stx)
    assert(blkChnTx.height === 2)
  }

  it should "allow repeated journaling of the same tx " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    ledger.journal(BlockTx(34, stx))
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    assert(blkChnTx.blockTx.index === 34)
    assert(blkChnTx.blockTx.signedTx === stx)
    assert(blkChnTx.height === 2)
  }

  it should "allow a tx to be committed by BlockId " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    ledger.commit(BlockId(2, 2))
    intercept[IllegalArgumentException] {ledger(stx) }
  }

  it should "allow a tx to be committed  " in {
    val stx = resetUTXOBlockAndCreateTx(2)
    val ledger = BlockChainLedger(2)
    val blkChnTx = ledger.journal(BlockTx(34, stx))
    ledger.commit
    intercept[IllegalArgumentException] {ledger(stx) }
  }
}
