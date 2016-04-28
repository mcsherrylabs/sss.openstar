package sss.asado.ledger

import contract.{NullDecumbrance, NullEncumbrance}
import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.SeedBytes
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */
class LedgerTest extends FlatSpec with Matchers {


  implicit val db: Db = Db()
  db.executeSql("TRUNCATE TABLE utxo ")
  val genisis = GenisesTx(outs = Seq(TxOutput(100, NullEncumbrance), TxOutput(100, NullEncumbrance), TxOutput(100, NullEncumbrance)))
  val ledger = Ledger()

  ledger.genesis(genisis)


  var validOut: TxIndex = _

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

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(11, NullEncumbrance))
    intercept[IllegalArgumentException] {
      val le = ledger.apply(SignedTx(StandardTx(ins, outs)))
    }
  }

  it should " prevent double spend " in {

    val ins = Seq(TxInput(TxIndex(genisis.txId, 1), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(99, NullEncumbrance), TxOutput(1, NullEncumbrance))
    val stx = SignedTx(StandardTx(ins, outs))
    validOut = TxIndex(stx.txId, 0)
    val le = ledger.apply(stx)

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

    val ins = Seq(TxInput(validOut, 99, NullDecumbrance))
    val outs = Seq(TxOutput(98, NullEncumbrance), TxOutput(1, NullEncumbrance))
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
