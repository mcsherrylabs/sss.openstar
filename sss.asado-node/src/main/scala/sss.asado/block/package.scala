package sss.asado

import akka.actor.ActorRef
import sss.asado.block.serialize._
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block._
import sss.asado.util.Serialize.ToBytes

/**
  * Created by alan on 3/24/16.
  */
package object block {

  trait GetLatestCommittedBlockId extends (() => BlockId)
  type GetLatestRecordedBlockId = () => BlockId

  case class BlockClosedEvent(heightClosed: Long) extends AsadoEvent

  // Fired when the client has downloaded up to the latest
  case object ClientSynced

  trait IsSynced extends AsadoEvent {
    val chainIdMask: GlobalChainIdMask
    val isSynced: Boolean
  }

  case class NotSynchronized(chainIdMask: GlobalChainIdMask)
    extends IsSynced {
    final val isSynced: Boolean = false
  }

  case class Synchronized(chainIdMask: GlobalChainIdMask, height: Long, index: Long, upStreamNodeId: UniqueNodeIdentifier)
    extends IsSynced {
    final val isSynced: Boolean = true
  }

  implicit object GetTxPageOrdering extends Ordering[GetTxPage] {

    override def compare(x: GetTxPage, y: GetTxPage): Int = {
      //if x < y negative
      if(x.blockHeight < y.blockHeight) -1
      else if (x.blockHeight == y.blockHeight) {
        if(x.index < y.index) -1
        else if(x.index == y.index) 0
        else 1
      } else 1
    }
  }

  case class GetTxPage(blockHeight: Long, index: Long, pageSize: Int = 50)
  case class VoteLeader(nodeId: UniqueNodeIdentifier, height: Long, committedTxIndex: Long)
  case class Leader(nodeId: UniqueNodeIdentifier)

  case class FindLeader(height: Long,
                        recordedTxIndex: Long,
                        committedTxIndex: Long,
                        signatureIndex: Int, nodeId: UniqueNodeIdentifier)

  implicit object FindLeaderOrdering extends Ordering[FindLeader] {
    override def compare(x: FindLeader, y: FindLeader): Int = {
      if(x.height < y.height) -1
      else if (x.height == y.height)
        if(x.committedTxIndex < y.committedTxIndex) -1
        else if(x.committedTxIndex == y.committedTxIndex)
          if(x.recordedTxIndex < y.recordedTxIndex) -1
          else if(x.recordedTxIndex == y.recordedTxIndex)
            if (x.signatureIndex > y.signatureIndex) -1
            else if(x.signatureIndex == y.signatureIndex)
              if(x.nodeId.hashCode < y.nodeId.hashCode) -1
              else if(x.nodeId.hashCode == y.nodeId.hashCode) 0
              else 1
            else 1
          else 1
        else 1
      else 1
    }
  }

  case class ReDistributeTx(blockChainTx: BlockChainTx)
  case class DistributeTx(client: ActorRef, blockChainTx: BlockChainTx)
  case class DistributeSig(blockSig: BlockSignature)
  case class DistributeClose(blockSigs: Seq[BlockSignature], blockId: BlockId)

  implicit class SynchronizedTo(sync: Synchronized) extends ToBytes {
    override def toBytes: Array[Byte] = SynchronizedSerializer.toBytes(sync)
  }
  implicit class SynchronizedFrom(b: Array[Byte]) {
    def toSynchronized: Synchronized = SynchronizedSerializer.fromBytes(b)
  }

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
