package sss.asado.ledger.serialize

import sss.asado.balanceledger._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.SinglePrivateKey
import sss.asado.ledger.TxId
import sss.asado.crypto.SeedBytes

/**
  * Created by alan on 2/15/16.
  */

class TxOutputTest extends FlatSpec with Matchers {

  val randomTxId: TxId = SeedBytes(32)
  val copyRandomTxId: TxId = java.util.Arrays.copyOf(randomTxId, randomTxId.length)
  val txIndex = TxIndex(randomTxId, 3456)
  val pkPair = PrivateKeyAccount()
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
