package sss.asado.ledger.serialize

import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */

object SignedTxTest {
  def createSignedTx = {
    val randomTxId: TxId = SeedBytes(32)
    val txIndex = TxIndex(randomTxId, 3456)
    val pkPair = PrivateKeyAccount()
    val txOutput = TxOutput(33, SinglePrivateKey(pkPair.publicKey))
    val txInput = TxInput(txIndex, 34, PrivateKeySig)
    val tx = StandardTx(Seq(txInput, txInput, txInput), Seq(txOutput, txOutput, txOutput))
    val sig = tx.sign(pkPair)
    SignedTx(tx, Seq(Seq(sig)))
  }
}
class SignedTxTest extends FlatSpec with Matchers {

  val randomTxId: TxId = SeedBytes(32)
  val txIndex = TxIndex(randomTxId, 3456)
  val pkPair = PrivateKeyAccount()
  val pkPair2 = PrivateKeyAccount()
  val txOutput = TxOutput(33, SinglePrivateKey(pkPair.publicKey))
  val txInput = TxInput(txIndex, 34, PrivateKeySig)
  val tx = StandardTx(Seq(txInput, txInput, txInput), Seq(txOutput, txOutput, txOutput))
  val sig = tx.sign(pkPair)
  val sig2 = tx.sign(pkPair2)
  val stx = SignedTx(tx, Seq(Seq(sig)))
  val stx2 = SignedTx(tx, Seq(Seq(sig), Seq(sig2)))
  val otherStxWithoutSig = SignedTx(tx)

  "A Signed Tx" should " be parseable to bytes " in {
    val bytes: Array[Byte] = stx.toBytes

  }

  it should " be parseable from bytes to an equal instance " in {
    val bytes: Array[Byte] = stx.toBytes
    val backAgain = bytes.toSignedTx
    assert(backAgain === stx)
    assert(backAgain.hashCode === stx.hashCode)
  }

  it should " be not equal to a different instance " in {
    assert(stx !== otherStxWithoutSig)
    assert(stx.hashCode !== otherStxWithoutSig.hashCode)
  }

  it should " match the sig to the inputs after deserialization " in {
    val bytes: Array[Byte] = stx2.toBytes
    val backAgain = bytes.toSignedTx
    assert(backAgain === stx2)
    assert(backAgain.params(0)(0) isSame stx2.params(0)(0))
    assert(backAgain.params(1)(0) === stx2.params(1)(0))
    assert(backAgain.params(1)(0) !== stx2.params(0)(0))
  }
}
