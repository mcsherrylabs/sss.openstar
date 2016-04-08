package sss.asado.block.serialize

import block._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.ledger.serialize.SignedTxTest
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */
class BlockSerializerTest extends FlatSpec with Matchers {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))

  val height = 33
  val id = 20000
  val stx = SignedTxTest.createSignedTx

  "A BlockChain Tx " should " be correctly serialised and deserialized " in {
    val c = BlockChainTx(height, BlockTx(9, stx))
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockChainTx
    assert(backAgain === c)

  }

  "A Block  id " should " be corrrectly serialised and deserialized " in {
    val c = BlockId(222, 3433)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockId
    assert(backAgain.blockHeight === c.blockHeight)

    assert(backAgain.numTxs == c.numTxs)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }

  "An Block chain Tx id " should " be corrrectly serialised and deserialized " in {
    val c = BlockChainTxId(height, BlockTxId(stx.txId, 34))
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockChainIdTx
    assert(backAgain.height === c.height)

    assert(backAgain.blockTxId.txId === c.blockTxId.txId)
    assert(backAgain.blockTxId.index === c.blockTxId.index)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }

  "An Block Tx id " should " be corrrectly serialised and deserialized " in {
    val c = BlockTxId(stx.txId, 34)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockIdTx
    assert(backAgain.index === c.index)

    assert(backAgain.txId === c.txId)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }

  "A Find Leader " should " be corrrectly serialised and deserialized " in {
    val c = FindLeader(1234, 4, "Holy Karelia!")
    val asBytes = c.toBytes
    val backAgain = asBytes.toFindLeader
    assert(backAgain.height === c.height)
    assert(backAgain.nodeId === c.nodeId)
    assert(backAgain === c)
  }

  "A Get Page Tx" should " be corrrectly serialised and deserialized " in {
    val c = GetTxPage(Long.MaxValue, 4, 45)
    val asBytes = c.toBytes
    val backAgain: GetTxPage = asBytes.toGetTxPage
    assert(backAgain.pageSize === c.pageSize)
    assert(backAgain.index === c.index)
    assert(backAgain.blockHeight === c.blockHeight)
    assert(backAgain === c)
  }
}
