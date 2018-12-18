package sss.openstar.ledger.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.account.PrivateKeyAccount
import sss.openstar.balanceledger._
import sss.openstar.contract.SinglePrivateKey
import sss.openstar.ledger.TxId

/**
  * Created by alan on 2/15/16.
  */

class TxOutputTest extends FlatSpec with Matchers {

  val randomTxId: TxId = DummySeedBytes.randomSeed(32)
  val copyRandomTxId: TxId = java.util.Arrays.copyOf(randomTxId, randomTxId.length)
  val txIndex = TxIndex(randomTxId, 3456)
  val pkPair = PrivateKeyAccount(DummySeedBytes)
  val txOutput = TxOutput(33, SinglePrivateKey(pkPair.publicKey))

  "A TxOutput" should " be parseable to bytes " in {
    val bytes: Array[Byte] = txOutput.toBytes
  }

  it should " be parseable from bytes to an equal instance " in {
    val bytes: Array[Byte] = txOutput.toBytes
    val backAgain = bytes.toTxOutput

    assert(backAgain.amount === txOutput.amount)
    assert(backAgain.encumbrance === txOutput.encumbrance)
    assert(backAgain === txOutput)

  }

}
