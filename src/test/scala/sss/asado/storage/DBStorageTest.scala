package sss.asado.storage

import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */
class DBStorageTest extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))
  val genisis = SignedTx((GenisesTx(outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey))))))
  val dbStorage = new DBStorage("DBStorageTest")
  dbStorage.write(genisis.txId, genisis)

  "DB storage " should " allow gensies ledger entries to be persisted " in {

    val retrieved = dbStorage(genisis.tx.txId)
    assert(retrieved == genisis)
  }

  it should " allow standard ledger entries to be persisted " in {

    val inputTx = dbStorage(genisis.tx.txId)

    val ins = Seq(TxInput(TxIndex(inputTx.tx.txId, 0), 100, PrivateKeySig))
    val outs = Seq(TxOutput(99, SinglePrivateKey(pkPair.publicKey)), TxOutput(1, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    val stx = SignedTx(tx)

    intercept[NoSuchElementException] {
      dbStorage(tx.txId)
    }

    dbStorage.write(stx.txId, stx)
    val retrieved = dbStorage(tx.txId)
    assert(retrieved == stx)
  }
}
