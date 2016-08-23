package sss.asado.block.serialize

import block._
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.block._
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.ledger.LedgerItem
import sss.asado.crypto.SeedBytes
import sss.asado.account.PrivateKeyAccount

import scala.util.Random

/**
  * Created by alan on 2/15/16.
  */
class BlockSerializerTest extends FlatSpec with Matchers {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))

  val height = 33
  val id = 20000
  val stx = LedgerItem(1, SeedBytes(32), SeedBytes(100))

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

  "A Block id " should " be corrrectly serialised and deserialized " in {
    val c = BlockId(222, 3433)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockId
    assert(backAgain.blockHeight === c.blockHeight)

    assert(backAgain.numTxs == c.numTxs)
    assert(backAgain.hashCode() === c.hashCode())
    assert(backAgain === c)
  }

  "A Block chain Tx id " should " be corrrectly serialised and deserialized " in {
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

  "An Block Tx id " should " be corrrectly serialised and deserialized " in {
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

  "A Find Leader " should " be corrrectly serialised and deserialized " in {
    val c = FindLeader(1234, 99, 4, "Holy Karelia!")
    val asBytes = c.toBytes
    val backAgain = asBytes.toFindLeader
    assert(backAgain.height === c.height)
    assert(backAgain.nodeId === c.nodeId)
    assert(backAgain === c)
  }

  "A Leader " should " be corrrectly serialised and deserialized " in {
    val c = Leader( "Holy Karelia!")
    val asBytes = c.toBytes
    val backAgain = asBytes.toLeader
    assert(backAgain.nodeId === c.nodeId)
    assert(backAgain === c)
  }

  "A Vote Leader " should " be corrrectly serialised and deserialized " in {
    val c = VoteLeader( "Holy Karelia!")
    val asBytes = c.toBytes
    val backAgain = asBytes.toVoteLeader
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

  "A block signature " should " be corrrectly serialised and deserialized " in {
    val pk = SeedBytes(Random.nextInt(200))
    val sig = SeedBytes(Random.nextInt(200))
    val sig2 = SeedBytes(Random.nextInt(200))
    val c = BlockSignature(23, new DateTime(), 45, "mycrazynode", pk, sig)
    val asBytes = c.toBytes
    val backAgain = asBytes.toBlockSignature
    assert(backAgain == c)
    assert(backAgain.index == c.index)
    assert(backAgain.savedAt == c.savedAt)
    assert(backAgain.hashCode() === c.hashCode())
    val c2 = BlockSignature(23, new DateTime(), 45, "mycrazynode", pk, sig2)
    assert(c !== c2)
    assert(c.hashCode() !== c2.hashCode())
    assert(backAgain !== c2)
  }

  "A tx message " should " be corrrectly serialised and deserialized " in {
    val txId = SeedBytes(Random.nextInt(50))
    val msg = TxMessage(3.toByte, txId, "Here we are now, entertain us, here we are now...")
    val asBytes = msg.toBytes
    val recovered = asBytes.toTxMessage
    assert(recovered.msg === msg.msg)
    assert(recovered.txId === msg.txId)
    assert(recovered.hashCode() === msg.hashCode())
    assert(recovered === msg)

  }
  "A block signature close block " should " be corrrectly serialised and deserialized " in {

    val sig2 = SeedBytes(Random.nextInt(200))
    val allSigs = (0 to 10) map { i =>
      val pk = SeedBytes(Random.nextInt(200))
      val sig = SeedBytes(Random.nextInt(200))
      BlockSignature(i, new DateTime(), 45 * i, s"${i}mycrazynode", pk, sig)
    }
    val bId = BlockId(222, 3433)

    val distClose = DistributeClose(allSigs, bId)
    val asBytes = distClose.toBytes
    val backAgain = asBytes.toDistributeClose
    assert(distClose.blockSigs.size === backAgain.blockSigs.size)
    assert(distClose.blockId === backAgain.blockId)
    assert(distClose === backAgain)
  }
}
