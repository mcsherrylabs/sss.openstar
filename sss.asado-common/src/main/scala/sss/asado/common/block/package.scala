package sss.asado.common

import com.google.common.primitives.Longs
import sss.asado.common.block.serialize._
import sss.asado.ledger._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.ByteArrayEncodedStrOps._

import sss.asado.util.Serialize.ToBytes

package object block {

  case class BlockId(blockHeight: Long, txIndex: Long)
  case class BlockTx(index: Long, ledgerItem: LedgerItem)
  case class BlockChainTx(height: Long, blockTx: BlockTx) {
    def toId: BlockChainTxId =
      BlockChainTxId(height, BlockTxId(blockTx.ledgerItem.txId, blockTx.index))
  }

  case class TxMessage(msgType: Byte, txId: TxId, msg: String)
    extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case txMsg: TxMessage =>
        txMsg.msg == msg &&
          txMsg.msgType == msgType &&
          txMsg.txId.isSame(txId)

      case _ => false
    }
    override def hashCode(): Int =
      java.util.Arrays.hashCode(txId) + msg.hashCode + msgType.hashCode

    override def toString: String = s"${txId.toBase64Str} $msg"
  }

  case class BlockTxId(txId: TxId, index: Long) extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case blockTxId: BlockTxId =>
        blockTxId.index == index &&
          blockTxId.txId.isSame(txId)

      case _ => false
    }

    override def toString: String = s"Index: ${index}, " + txId.toBase64Str

    override def hashCode(): Int = Longs.hashCode(index) + (17 * txId.hash)
  }

  case class BlockChainTxId(height: Long, blockTxId: BlockTxId)
    extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case blockChainTxId: BlockChainTxId =>
        blockChainTxId.height == height &&
          blockChainTxId.blockTxId == blockTxId

      case _ => false
    }

    override def toString: String = s"Height: ${height}, $blockTxId"
    override def hashCode(): Int = Longs.hashCode(height) + blockTxId.hashCode()
  }

  implicit object BlockIdOrdering extends Ordering[BlockId] {

    override def compare(x: BlockId, y: BlockId): Int = {
      //if x < y negative
      if(x.blockHeight < y.blockHeight) -1
      else if (x.blockHeight == y.blockHeight) {
        if(x.txIndex < y.txIndex) -1
        else if(x.txIndex == y.txIndex) 0
        else 1
      } else 1
    }
  }

  implicit object BlockChainTxIdOrdering extends Ordering[BlockChainTxId] {
    override def compare(x: BlockChainTxId, y: BlockChainTxId): Int = {
      //if x < y negative
      if(x.height < y.height) -1
      else if (x.height == y.height) {
        if(x.blockTxId.index < y.blockTxId.index) -1
        else if(x.blockTxId.index == y.blockTxId.index) 0
        else 1
      } else 1
    }
  }

  implicit object BlockChainTxOrdering extends Ordering[BlockChainTx] {
    override def compare(x: BlockChainTx, y: BlockChainTx): Int =
      BlockChainTxIdOrdering.compare(x.toId, y.toId)
  }


  implicit class BlockChainIdTxTo(t: BlockChainTxId) extends ToBytes {
    override def toBytes: Array[Byte] = BlockChainTxIdSerializer.toBytes(t)
  }
  implicit class BlockChainIdTxFrom(b: Array[Byte]) {
    def toBlockChainTxId: BlockChainTxId = BlockChainTxIdSerializer.fromBytes(b)
  }
  implicit class BlockIdTxTo(t: BlockTxId) extends ToBytes {
    override def toBytes: Array[Byte] = BlockTxIdSerializer.toBytes(t)
  }
  implicit class BlockIdTxFrom(b: Array[Byte]) {
    def toBlockIdTx: BlockTxId = BlockTxIdSerializer.fromBytes(b)
  }
  implicit class BlockChainTxTo(t: BlockChainTx) extends ToBytes {
    override def toBytes: Array[Byte] = BlockChainTxSerializer.toBytes(t)
  }
  implicit class BlockChainTxFrom(b: Array[Byte]) {
    def toBlockChainTx: BlockChainTx = BlockChainTxSerializer.fromBytes(b)
  }
  implicit class BlockTxTo(t: BlockTx) extends ToBytes {
    override def toBytes: Array[Byte] = BlockTxSerializer.toBytes(t)
  }
  implicit class BlockTxFrom(b: Array[Byte]) {
    def toBlockTx: BlockTx = BlockTxSerializer.fromBytes(b)
  }
  implicit class BlockIdTo(t: BlockId) extends ToBytes {
    override def toBytes: Array[Byte] = BlockIdSerializer.toBytes(t)
  }
  implicit class BlockIdFrom(b: Array[Byte]) {
    def toBlockId: BlockId = BlockIdSerializer.fromBytes(b)
  }
  implicit class TxMessageTo(t: TxMessage) extends ToBytes {
    override def toBytes: Array[Byte] = TxMessageSerializer.toBytes(t)
  }
  implicit class TxMessageFrom(b: Array[Byte]) {
    def toTxMessage: TxMessage = TxMessageSerializer.fromBytes(b)
  }
}
