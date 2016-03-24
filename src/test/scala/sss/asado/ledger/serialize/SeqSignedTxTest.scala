package sss.asado.ledger.serialize

import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */

class SeqSignedTxTest extends FlatSpec with Matchers {


  def stx(index: Int = 0): SignedTx = {
    val randomTxId: TxId = SeedBytes(32)
    val txIndex = TxIndex(randomTxId, 3456 + index)
    val pkPair = PrivateKeyAccount()
    val txOutput = TxOutput(33 + index, SinglePrivateKey(pkPair.publicKey))
    val txInput = TxInput(txIndex, 34 + index, PrivateKeySig)
    val tx = StandardTx(Seq(txInput, txInput, txInput), Seq(txOutput, txOutput, txOutput))
    val sig = tx.sign(pkPair)
    SignedTx(tx, Seq(sig))
  }

  lazy val stxs = 0 to 10 map stx _

  "A Seq of Signed Tx" should " be parseable to bytes " in {
    val seqStxs = SeqSignedTx(stxs)
    val bytes: Array[Byte] = seqStxs.toBytes
  }

  it should " be parseable from bytes to equal instances " in {
    val seqStxs = SeqSignedTx(stxs)
    val bytes: Array[Byte] = seqStxs.toBytes
    val backAgain = bytes.toSeqSignedTx
    val zipped = backAgain.ordered.zip(seqStxs.ordered)
    zipped.foreach { case (a, b) => assert(a == b) }
  }

}
