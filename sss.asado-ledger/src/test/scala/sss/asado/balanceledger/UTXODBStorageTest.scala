package sss.asado.balanceledger

import java.sql.SQLIntegrityConstraintViolationException

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.SinglePrivateKey
import sss.asado.crypto.SeedBytes
import sss.asado.ledger.SignedTxEntry
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */
class UTXODBStorageTest extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))
  val genisis = SignedTxEntry((StandardTx(ins = Seq(), outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)),
    TxOutput(100, SinglePrivateKey(pkPair.publicKey)))).toBytes))

  lazy val tx = genisis.txEntryBytes.toTx
  implicit val db = Db()
  val r = db.executeSql("TRUNCATE TABLE utxo;")
  println(s"Got $r from sql")
  val dbStorage = new UTXODBStorage


  "UTXO Storage " should " allow outputs to be persisted " in {

    tx.outs.indices foreach { i =>
      dbStorage.write(TxIndex(genisis.txId, i), tx.outs(i))
    }

    dbStorage.entries.map(println(_))
  }

  it should "prevent double inputs " in {

    intercept[SQLIntegrityConstraintViolationException] {
      dbStorage.write(TxIndex(genisis.txId, 0), tx.outs(0))
    }

  }

  it should "allow retrieval of outputs " in {

    val txOutput = dbStorage(TxIndex(genisis.txId, 0))

    assert(txOutput.amount === 100)
    val asPKeyEnc = txOutput.encumbrance.asInstanceOf[SinglePrivateKey]
    assert(asPKeyEnc.pKey === pkPair.publicKey)

  }


  it should " differentiate between indexes " in {

    val txOutput = dbStorage(TxIndex(genisis.txId, 0))
    dbStorage.delete(TxIndex(genisis.txId, 0))

    intercept[NoSuchElementException] {
      dbStorage(TxIndex(genisis.txId, 0))
    }

    val txOutput2 = dbStorage(TxIndex(genisis.txId, 1))
    assert(txOutput2.amount === 100)

  }
}
