package sss.asado.contract

import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by alan on 2/15/16.
  */
class ContractTest extends FlatSpec with Matchers {


  /*lazy val pkPair = PrivateKeyAccount(SeedBytes(32))


  val genisis = SignedTx(GenisesTx(outs = Seq(TxOutput(100, NullEncumbrance))))


  "A single sig " should " unlock a single key contract " in {

    val ledger = new UTXOLedger(new UTXODBStorage(genisis))

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    ledger(SignedTx(tx))

    val ins2 = Seq(TxInput(TxIndex(tx.txId, 0), 100,  PrivateKeySig))
    val outs2 = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx2 = StandardTx(ins2, outs2)

    val sig = EllipticCurveCrypto.sign(pkPair.privateKey, tx2.txId)
    ledger(SignedTx(tx2,Seq(sig)))

  }


  "A ledger " should " not accept a contract using a bad txId " in {

    val ledger = new UTXOLedger(new MemoryStorage(genisis))

    val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
    val outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx = StandardTx(ins, outs)
    ledger(SignedTx(tx))

    val ins2 = Seq(TxInput(TxIndex(tx.txId, 0), 100,  PrivateKeySig))
    val outs2 = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
    val tx2 = StandardTx(ins2, outs2)

    val sig = EllipticCurveCrypto.sign(pkPair.privateKey, SeedBytes(10))
    intercept[IllegalArgumentException] {
      ledger(SignedTx(tx2, Seq(sig)))
    }

  }

  it should "not accept a contract using a bad key sig" in {

    val ledger = new UTXOLedger(new MemoryStorage(genisis))

  val wrongPkPair = PrivateKeyAccount(SeedBytes(20))

  val ins = Seq(TxInput(TxIndex(genisis.txId, 0), 100,  NullDecumbrance))
  val outs = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
  val tx = StandardTx(ins, outs)
  ledger(SignedTx(tx))

  val ins2 = Seq(TxInput(TxIndex(tx.txId, 0), 100,  PrivateKeySig))
  val outs2 = Seq(TxOutput(100, SinglePrivateKey(pkPair.publicKey)))
  val tx2 = StandardTx(ins2, outs2)

  val sig = EllipticCurveCrypto.sign(wrongPkPair.privateKey, tx2.txId)
  intercept[IllegalArgumentException] {
    ledger(SignedTx(tx2, Seq(sig)))
  }

}*/
}
