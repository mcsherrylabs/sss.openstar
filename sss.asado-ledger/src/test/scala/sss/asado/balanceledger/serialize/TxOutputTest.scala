package sss.asado.ledger.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.PrivateKeyAccount
import sss.asado.balanceledger._
import sss.asado.contract.SinglePrivateKey
import sss.asado.ledger.TxId

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
