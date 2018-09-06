package sss.asado.block.serialize

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.PrivateKeyAccount
import sss.asado.block._
import sss.asado.ledger.LedgerItem

import scala.util.Random

/**
  * Created by alan on 2/15/16.
  */
class BlockSerializerTest extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount(DummySeedBytes(32))

  val height = 33
  val id = 20000
  val stx = LedgerItem(1, DummySeedBytes(32), DummySeedBytes(100))

  "A BlockChain Tx " should " be correctly serialised and deserialized " in {
    val c = BlockChainTx(height, BlockTx(9, stx))
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockChainTx
    assert(backAgain === c)
  }

  it should " provide the correct BlockChainTxId " in {
    val c = BlockChainTx(height, BlockTx(9, stx))
    val blockChainTxId = c.toId

    assert(blockChainTxId.height === c.height)
    assert(blockChainTxId.blockTxId.index === c.blockTx.index)
    assert(blockChainTxId.blockTxId.txId === c.blockTx.ledgerItem.txId)
  }

  "A Block id " should " be correctly serialised and deserialized " in {
    val c = BlockId(222, 3433)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockId
    assert(backAgain.blockHeight === c.blockHeight)

    assert(backAgain.numTxs == c.numTxs)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }

  "A Block chain Tx id " should " be correctly serialised and deserialized " in {
    val c = BlockChainTxId(height, BlockTxId(stx.txId, 34))
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockChainIdTx
    assert(backAgain.height === c.height)

    assert(backAgain.blockTxId.txId === c.blockTxId.txId)
    assert(backAgain.blockTxId.index === c.blockTxId.index)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }
  it should "have the index and txid in it's toString" in {
    import sss.asado.util.ByteArrayEncodedStrOps._
    val c = BlockChainTxId(height, BlockTxId(stx.txId, 34)).toString
    assert(c.contains(s"$height"))
    assert(c.contains(s"34"))
    assert(c.contains(stx.txId.toBase64Str))
  }

  "An Block Tx id " should " be correctly serialised and deserialized " in {
    val c = BlockTxId(stx.txId, 34)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockIdTx
    assert(backAgain.index === c.index)

    assert(backAgain.txId === c.txId)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }

  it should "have the index and txid in it's toString" in {
    import sss.asado.util.ByteArrayEncodedStrOps._

    val c = BlockTxId(stx.txId, 34).toString
    assert(c.contains("34"))
    assert(c.contains(stx.txId.toBase64Str))
  }

  "A tx message " should " be correctly serialised and deserialized " in {
    val txId = DummySeedBytes(Random.nextInt(50))
    val msg = TxMessage(3.toByte,
                        txId,
                        "Here we are now, entertain us, here we are now...")
    val asBytes = msg.toBytes
    val recovered = asBytes.toTxMessage
    assert(recovered.msg === msg.msg)
    assert(recovered.txId === msg.txId)
    assert(recovered.hashCode() === msg.hashCode())
    assert(recovered === msg)

  }

}
