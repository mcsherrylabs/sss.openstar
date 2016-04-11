
import akka.actor.ActorRef
import com.google.common.primitives.Longs
import ledger.{SignedTx, TxId}
import sss.asado.block.serialize._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.ByteArrayVarcharOps.ByteArrayToVarChar
import sss.asado.util.Serialize.ToBytes

/**
  * Created by alan on 3/24/16.
  */
package object block {

  case class BlockId(blockHeight: Long, numTxs: Long)
  case class BlockTx(index: Long, signedTx: SignedTx)
  case class BlockChainTx(height: Long, blockTx: BlockTx) {
    def toId: BlockChainTxId = BlockChainTxId(height, BlockTxId(blockTx.signedTx.txId, blockTx.index))
  }

  case class GetTxPage(blockHeight: Long, index: Long, pageSize: Int = 10)

  case class VoteLeader(nodeId: String)
  case class Leader(nodeId: String)
  case class FindLeader(height: Long, commitedTxIndex: Long, signatureIndex: Int, nodeId: String)

  case class ReDistributeTx(blockChainTx: BlockChainTx)
  case class DistributeTx(client: ActorRef, blockChainTx: BlockChainTx)
  case class DistributeClose(blockId: BlockId)


  case class BlockTxId(txId: TxId, index: Long) extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case blockTxId: BlockTxId => blockTxId.index == index &&
        blockTxId.txId.isSame(txId)

      case _ => false
    }

    override def toString: String = s"Index: ${index}, " + txId.toVarChar

    override def hashCode(): Int = Longs.hashCode(index) + (17 * txId.hash)
  }

  case class BlockChainTxId(height: Long, blockTxId: BlockTxId) extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case blockChainTxId: BlockChainTxId => blockChainTxId.height == height &&
        blockChainTxId.blockTxId == blockTxId

      case _ => false
    }

    override def toString: String = s"Height: ${height}, $blockTxId"
    override def hashCode(): Int = Longs.hashCode(height) + blockTxId.hashCode()
  }

  implicit class GetTxPageTo(getTxPage: GetTxPage) extends ToBytes[GetTxPage] {
    override def toBytes: Array[Byte] = GetTxPageSerializer.toBytes(getTxPage)
  }
  implicit class GetTxPageFrom(b: Array[Byte]) {
    def toGetTxPage: GetTxPage = GetTxPageSerializer.fromBytes(b)
  }

  implicit class FindLeaderTo(lb: FindLeader) extends ToBytes[FindLeader] {
    override def toBytes: Array[Byte] = FindLeaderSerializer.toBytes(lb)
  }
  implicit class FindLeaderFrom(b: Array[Byte]) {
    def toFindLeader: FindLeader = FindLeaderSerializer.fromBytes(b)
  }

  implicit class LeaderTo(vl: Leader) extends ToBytes[Leader] {
    override def toBytes: Array[Byte] = LeaderSerializer.toBytes(vl)
  }
  implicit class LeaderFrom(b: Array[Byte]) {
    def toLeader: Leader = LeaderSerializer.fromBytes(b)
  }
  implicit class VoteLeaderTo(vl: VoteLeader) extends ToBytes[VoteLeader] {
    override def toBytes: Array[Byte] = VoteLeaderSerializer.toBytes(vl)
  }
  implicit class VoteLeaderFrom(b: Array[Byte]) {
    def toVoteLeader: VoteLeader = VoteLeaderSerializer.fromBytes(b)
  }

  implicit class BlockChainIdTxTo(t: BlockChainTxId) extends ToBytes[BlockChainTxId] {
    override def toBytes: Array[Byte] = BlockChainTxIdSerializer.toBytes(t)
  }
  implicit class BlockChainIdTxFrom(b: Array[Byte]) {
    def toBlockChainIdTx: BlockChainTxId = BlockChainTxIdSerializer.fromBytes(b)
  }

  implicit class BlockIdTxTo(t: BlockTxId) extends ToBytes[BlockTxId] {
    override def toBytes: Array[Byte] = BlockTxIdSerializer.toBytes(t)
  }
  implicit class BlockIdTxFrom(b: Array[Byte]) {
    def toBlockIdTx: BlockTxId = BlockTxIdSerializer.fromBytes(b)
  }

  implicit class BlockChainTxTo(t: BlockChainTx) extends ToBytes[BlockChainTx] {
    override def toBytes: Array[Byte] = BlockChainTxSerializer.toBytes(t)
  }
  implicit class BlockChainTxFrom(b: Array[Byte]) {
    def toBlockChainTx: BlockChainTx = BlockChainTxSerializer.fromBytes(b)
  }
  implicit class BlockTxTo(t: BlockTx) extends ToBytes[BlockTx] {
    override def toBytes: Array[Byte] = BlockTxSerializer.toBytes(t)
  }
  implicit class BlockTxFrom(b: Array[Byte]) {
    def toBlockTx: BlockTx = BlockTxSerializer.fromBytes(b)
  }

  implicit class BlockIdTo(t: BlockId) extends ToBytes[BlockId] {
    override def toBytes: Array[Byte] = BlockIdSerializer.toBytes(t)
  }
  implicit class BlockIdFrom(b: Array[Byte]) {
    def toBlockId: BlockId = BlockIdSerializer.fromBytes(b)
  }
}
