package sss.asado.contract

import contract.{NullDecumbrance, NullEncumbrance}
import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.ledger.Ledger
import sss.asado.storage.MemoryStorage
import sss.asado.util.{EllipticCurveCrypto, SeedBytes}

/**
  * Created by alan on 2/15/16.
  */
class ContractTest extends FlatSpec with Matchers {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))


  val genisis = SignedTx(GenisesTx(outs = Seq(TxOutput(100, NullEncumbrance))))


  "A single sig " should " unlock a single key contract " in {

    val ledger = new Ledger(new MemoryStorage(genisis))

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    ledger(SignedTx(tx))

    val ins2 = Seq(TxInput(TxIndex(tx.txId, 0), 100,  PrivateKeySig))
    val outs2 = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx2 = StandardTx(ins2, outs2)

    val sig = EllipticCurveCrypto.sign(pkPair.privateKey.array, tx2.txId.array)
    ledger(SignedTx(tx2,Seq(sig)))

  }


  "A ledger " should " not accept a contract using a bad txId " in {

    val ledger = new Ledger(new MemoryStorage(genisis))

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    ledger(SignedTx(tx))

    val ins2 = Seq(TxInput(TxIndex(tx.txId, 0), 100,  PrivateKeySig))
    val outs2 = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx2 = StandardTx(ins2, outs2)

    val sig = EllipticCurveCrypto.sign(pkPair.privateKey.array, SeedBytes(10))
    intercept[IllegalArgumentException] {
      ledger(SignedTx(tx2, Seq(sig)))
    }

  }

  it should "not accept a contract using a bad key sig" in {

    val ledger = new Ledger(new MemoryStorage(genisis))

  val wrongPkPair = PrivateKeyAccount(SeedBytes(20))

  val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
  val outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
  val tx = StandardTx(ins, outs)
  ledger(SignedTx(tx))

  val ins2 = Seq(TxInput(TxIndex(tx.txId, 0), 100,  PrivateKeySig))
  val outs2 = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
  val tx2 = StandardTx(ins2, outs2)

  val sig = EllipticCurveCrypto.sign(wrongPkPair.privateKey.array, tx2.txId.array)
  intercept[IllegalArgumentException] {
    ledger(SignedTx(tx2, Seq(sig)))
  }

}
}
