
import akka.actor.ActorRef
import sss.asado.AsadoEvent
import sss.asado.block.serialize._
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.block.{BlockChainTx, BlockId}
import sss.asado.util.Serialize.ToBytes

/**
  * Created by alan on 3/24/16.
  */
package object block {

  case class BlockClosedEvent(heightClosed: Long) extends AsadoEvent

  // Fired when the client has downloaded up to the latest
  case object ClientSynced
  // Fired when the network leader has got a quorum of synced nodes
  case object IsSynced
  case object NotSynced

  case class GetTxPage(blockHeight: Long, index: Long, pageSize: Int = 50)

  case class VoteLeader(nodeId: String)
  case class Leader(nodeId: String)
  case class FindLeader(height: Long, commitedTxIndex: Long, signatureIndex: Int, nodeId: String)

  case class ReDistributeTx(blockChainTx: BlockChainTx)
  case class DistributeTx(client: ActorRef, blockChainTx: BlockChainTx)
  case class DistributeSig(blockSig: BlockSignature)
  case class DistributeClose(blockSigs: Seq[BlockSignature], blockId: BlockId)

  implicit class GetTxPageTo(getTxPage: GetTxPage) extends ToBytes {
    override def toBytes: Array[Byte] = GetTxPageSerializer.toBytes(getTxPage)
  }
  implicit class GetTxPageFrom(b: Array[Byte]) {
    def toGetTxPage: GetTxPage = GetTxPageSerializer.fromBytes(b)
  }

  implicit class FindLeaderTo(lb: FindLeader) extends ToBytes {
    override def toBytes: Array[Byte] = FindLeaderSerializer.toBytes(lb)
  }
  implicit class FindLeaderFrom(b: Array[Byte]) {
    def toFindLeader: FindLeader = FindLeaderSerializer.fromBytes(b)
  }

  implicit class LeaderTo(vl: Leader) extends ToBytes {
    override def toBytes: Array[Byte] = LeaderSerializer.toBytes(vl)
  }
  implicit class LeaderFrom(b: Array[Byte]) {
    def toLeader: Leader = LeaderSerializer.fromBytes(b)
  }
  implicit class VoteLeaderTo(vl: VoteLeader) extends ToBytes {
    override def toBytes: Array[Byte] = VoteLeaderSerializer.toBytes(vl)
  }
  implicit class VoteLeaderFrom(b: Array[Byte]) {
    def toVoteLeader: VoteLeader = VoteLeaderSerializer.fromBytes(b)
  }

  implicit class BlockSignatureTo(t: BlockSignature) extends ToBytes {
    override def toBytes: Array[Byte] = BlockSignatureSerializer.toBytes(t)
  }
  implicit class BlockSignatureFrom(b: Array[Byte]) {
    def toBlockSignature: BlockSignature = BlockSignatureSerializer.fromBytes(b)
  }

  implicit class DistributeCloseTo(t: DistributeClose) extends ToBytes {
    override def toBytes: Array[Byte] = DistributeCloseSerializer.toBytes(t)
  }
  implicit class DistributeCloseFrom(b: Array[Byte]) {
    def toDistributeClose: DistributeClose = DistributeCloseSerializer.fromBytes(b)
  }

}
