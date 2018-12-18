package sss.openstar.ledger.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.balanceledger._

/**
  * Created by alan on 2/15/16.
  */


class TxIndexTest extends FlatSpec with Matchers   {


  type TxId = Array[Byte]
  val randomTxId: TxId = DummySeedBytes.randomSeed(32)
  val copyRandomTxId: TxId = java.util.Arrays.copyOf(randomTxId, randomTxId.length)
  val txIndex = TxIndex(randomTxId, 3456)

  "A TxIndex" should " be parseable to bytes " in {
    val bytes: Array[Byte] = txIndex.toBytes
  }

  it should " be parseable from bytes to same instance " in {
    val bytes: Array[Byte] = txIndex.toBytes
    val backAgain = bytes.toTxIndex

    assert(backAgain.index === txIndex.index)
    assert(backAgain.txId === txIndex.txId)
    assert(backAgain === txIndex)

  }

  " TxIndex case classes created from same elements " should " be equal " in {
    val a = TxIndex(randomTxId, 342)
    val b = TxIndex(copyRandomTxId, 342)
    assert(randomTxId.isSame(copyRandomTxId))
    assert(a.txId.isSame(b.txId))
  }

  " TxIndex case classes created from different elements " should " not be equal " in {
    val a = TxIndex(randomTxId, 341)
    val b = TxIndex(copyRandomTxId, 342)
    assert(a !== b)

  }

  " TxIndex case classes created from same elements " should " have the same hashcode " in {
    val a = TxIndex(randomTxId, 342)
    val b = TxIndex(copyRandomTxId, 342)
    assert(a.hashCode === b.hashCode())

  }
}
