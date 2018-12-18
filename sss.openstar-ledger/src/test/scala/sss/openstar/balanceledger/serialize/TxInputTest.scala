package sss.openstar.ledger.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.balanceledger._
import sss.openstar.contract.PrivateKeySig
import sss.openstar.ledger.TxId

/**
  * Created by alan on 2/15/16.
  */

class TxInputTest extends FlatSpec with Matchers {

  val randomTxId: TxId = DummySeedBytes.randomSeed((32))
  val copyRandomTxId: TxId = java.util.Arrays.copyOf(randomTxId, randomTxId.length)
  val txIndex = TxIndex(randomTxId, 3456)
  val txInput = TxInput(txIndex, 33, PrivateKeySig)

  "A TxInput" should " be parseable to bytes " in {
    val bytes: Array[Byte] = txInput.toBytes
  }

  it should " be parseable from bytes to same instance " in {
    val bytes: Array[Byte] = txInput.toBytes
    val backAgain = bytes.toTxInput

    assert(backAgain.amount === txInput.amount)
    assert(backAgain.txIndex === txInput.txIndex)
    assert(backAgain.sig === txInput.sig)
    assert(backAgain === txInput)

  }

}
