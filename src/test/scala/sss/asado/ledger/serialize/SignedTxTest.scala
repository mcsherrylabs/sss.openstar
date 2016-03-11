package sss.asado.ledger.serialize

import ledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */

class SignedTxTest extends FlatSpec with Matchers {

  val randomTxId: TxId = SeedBytes(32)
  val txIndex = TxIndex(randomTxId, 3456)
  val pkPair = PrivateKeyAccount()
  val txOutput = TxOutput(33, SinglePrivateKey(pkPair.publicKey))
  val txInput = TxInput(txIndex, 34, PrivateKeySig)
  val tx = StandardTx(Seq(txInput, txInput, txInput), Seq(txOutput, txOutput, txOutput))
  val sig = tx.sign(pkPair)
  val stx = SignedTx(tx, Seq(sig))

  "A Signed Tx" should " be parseable to bytes " in {
    val bytes: Array[Byte] = stx.toBytes
  }

  it should " be parseable from bytes to an equal instance " in {
    val bytes: Array[Byte] = stx.toBytes
    val backAgain = bytes.toSignedTx
    assert(backAgain.tx === stx.tx)

    var i = 0
    backAgain.params.map { p =>
      val o = stx.params(i)
      i = i + 1
      var j = 0
      o.foreach { jj =>
        assert(jj == p(j))
        j += 1
      }

    }

  }

}
