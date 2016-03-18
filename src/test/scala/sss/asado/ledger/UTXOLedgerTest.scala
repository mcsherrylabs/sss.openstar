package sss.asado.ledger

import contract.{NullDecumbrance, NullEncumbrance}
import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.storage.MemoryStorage
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */
class UTXOLedgerTest extends FlatSpec with Matchers {


  val genisis = SignedTx(GenisesTx(outs = Seq(TxOutput(100, NullEncumbrance), TxOutput(100, NullEncumbrance), TxOutput(100, NullEncumbrance))))
  lazy val ledger = new UTXOLedger(new MemoryStorage(genisis))


  /*
  prevent negative
  prevent out > in
  prevent badly decumbred txs
   */

  "A Ledger " should " prevent txs with bad balances " in {

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 1000,  NullDecumbrance))
    val outs = Seq(TxOutput(1, NullEncumbrance), TxOutput(1, NullEncumbrance))
    intercept[IllegalArgumentException] {
      val le = ledger.apply(SignedTx(StandardTx(ins, outs)))
    }
  }

  it should " prevent txs with no inputs " in {

    val ins: Seq[TxInput] = Seq()
    val outs: Seq[TxOutput] = Seq()
    intercept[IllegalArgumentException] {
      val le = ledger.apply(SignedTx(StandardTx(ins, outs)))
    }
  }

  it should " not allow out balance to be greater than in " in {


    //val inputTx = ledger.entry(genisis.txId)

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(11, NullEncumbrance))
    intercept[IllegalArgumentException] {
      val le = ledger.apply(SignedTx(StandardTx(ins, outs)))
    }
  }

  it should " prevent double spend " in {

    val ins = Seq(TxInput(TxIndex(genisis.txId, 1), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val le = ledger.apply(SignedTx(StandardTx(ins, outs)))

    intercept[IllegalArgumentException] {
      ledger.apply(SignedTx(StandardTx(ins, outs)))
    }
  }

  it should " prevent spend from invalid tx in" in {

    val ins = Seq(TxInput(TxIndex(SeedBytes(3), 2), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    intercept[IllegalArgumentException] {
      ledger.apply(SignedTx(StandardTx(ins, outs)))
    }
  }

  it should " allow spending from a tx out that was also a tx " in {

    val ledger = new UTXOLedger(new MemoryStorage(genisis))

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100, NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val stx = SignedTx(StandardTx(ins, outs))
    ledger(stx)
    val nextIns = Seq(TxInput(TxIndex(stx.tx.txId, 1), 1, NullDecumbrance))
    val nextOuts = Seq(TxOutput(1, NullEncumbrance))
    val nextTx = SignedTx(StandardTx(nextIns, nextOuts))
    ledger(nextTx)

    intercept[IllegalArgumentException] {
      ledger(stx)
    }

    intercept[IllegalArgumentException] {
      ledger(nextTx)
    }
  }
}
