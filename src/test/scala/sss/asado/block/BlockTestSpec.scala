package sss.asado.block

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
  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))

  implicit val db = Db("DBStorageTest")
  val dbStorage = new Block(99)
  lazy val genisis = SignedTx((GenisesTx(outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey))))))
  lazy val createGenesis = {
    dbStorage.writeCommitted(genisis.txId, genisis)
    genisis
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

  "Tx DB storage " should " allow gensies ledger entries to be persisted " in {

    createGenesis
    val retrieved = dbStorage(genisis.tx.txId)
    assert(retrieved.signedTx == genisis)
  }

  it should " allow standard ledger entries to be persisted " in {

    val inputTx = dbStorage(genisis.tx.txId)

    val stx = createSignedTx (inputTx.signedTx)

    intercept[NoSuchElementException] {
      dbStorage(stx.txId)
    }

    dbStorage.writeCommitted(stx.txId, stx)
    val retrieved = dbStorage(stx.txId)
    assert(retrieved.signedTx == stx)
  }

  "FindSmallest " should "find the smallest missing  " in {

    var seq = Seq[Long]()
    assert(Block.findSmallestMissing(seq) == 0)
    seq = Seq[Long](1)
    assert(Block.findSmallestMissing(seq) == 1)
    seq = Seq[Long](1,2)
    assert(Block.findSmallestMissing(seq) == 2)
    seq = Seq[Long](1,2, 4, 5, 6)
    assert(Block.findSmallestMissing(seq) == 2)
    seq = Seq[Long](1,2, 3, 4, 6)
    assert(Block.findSmallestMissing(seq) == 4)
  }
}
