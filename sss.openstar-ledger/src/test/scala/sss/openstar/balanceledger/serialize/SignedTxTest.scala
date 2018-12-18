package sss.openstar.ledger.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.account.PrivateKeyAccount
import sss.openstar.balanceledger._
import sss.openstar.contract.{PrivateKeySig, SinglePrivateKey}
import sss.openstar.ledger._

/**
  * Created by alan on 2/15/16.
  */

object SignedTxTest {
  def createSignedTx = {
    val randomTxId: TxId = DummySeedBytes.randomSeed(32)
    val txIndex = TxIndex(randomTxId, 3456)
    val pkPair = PrivateKeyAccount(DummySeedBytes)
    val txOutput = TxOutput(33, SinglePrivateKey(pkPair.publicKey))
    val txInput = TxInput(txIndex, 34, PrivateKeySig)
    val tx = StandardTx(Seq(txInput, txInput, txInput), Seq(txOutput, txOutput, txOutput))
    val sig = pkPair.sign(tx.txId)
    SignedTxEntry(tx.toBytes, Seq(Seq(sig)))
  }
}
class SignedTxTest extends FlatSpec with Matchers {

  val randomTxId: TxId = DummySeedBytes.randomSeed(32)
  val txIndex = TxIndex(randomTxId, 3456)
  val pkPair = PrivateKeyAccount(DummySeedBytes)
  val pkPair2 = PrivateKeyAccount(DummySeedBytes)
  val txOutput = TxOutput(33, SinglePrivateKey(pkPair.publicKey))
  val txInput = TxInput(txIndex, 34, PrivateKeySig)
  val tx = StandardTx(Seq(txInput, txInput, txInput), Seq(txOutput, txOutput, txOutput))
  val sig = pkPair.sign(tx.txId)
  val sig2 = pkPair2.sign(tx.txId)
  val stx = SignedTxEntry(tx.toBytes, Seq(Seq(sig)))
  val stx2 = SignedTxEntry(tx.toBytes, Seq(Seq(sig), Seq(sig2)))
  val otherStxWithoutSig = SignedTxEntry(tx.toBytes)

  "A Signed Tx" should " be parseable to bytes " in {
    val bytes: Array[Byte] = stx.toBytes

  }

  it should " be parseable from bytes to an equal instance " in {
    val bytes: Array[Byte] = stx.toBytes
    val backAgain = bytes.toSignedTxEntry
    assert(backAgain === stx)
    assert(backAgain.hashCode === stx.hashCode)
  }

  it should " be not equal to a different instance " in {
    assert(stx !== otherStxWithoutSig)
    assert(stx.hashCode !== otherStxWithoutSig.hashCode)
  }

  it should " match the sig to the inputs after deserialization " in {
    val bytes: Array[Byte] = stx2.toBytes
    val backAgain = bytes.toSignedTxEntry
    assert(backAgain === stx2)
    assert(backAgain.signatures(0)(0) isSame stx2.signatures(0)(0))
    assert(backAgain.signatures(1)(0) === stx2.signatures(1)(0))
    assert(backAgain.signatures(1)(0) !== stx2.signatures(0)(0))
  }
}
