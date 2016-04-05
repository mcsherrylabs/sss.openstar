
import akka.actor.ActorRef
import ledger.{SignedTx, TxId}
import sss.asado.block.serialize._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.Serialize.ToBytes

/**
  * Created by alan on 3/24/16.
  */
package object block {

  case class GetTxPage(blockHeight: Long, index: Long, pageSize: Int = 10)

  case class VoteLeader(nodeId: String)
  case class Leader(nodeId: String)
  case class FindLeader(height: Long, signatureIndex: Int, nodeId: String)

  case class ReDistributeTx(signedTx: SignedTx, height: Long)
  case class DistributeTx(client: ActorRef, signedTx: SignedTx, height: Long)
  case class ConfirmTx(stx: SignedTx, height: Long)
  case class AckConfirmTx(txId: TxId, height: Long) extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case ackConfirm: AckConfirmTx => ackConfirm.height == height &&
        ackConfirm.txId.isSame(txId)

      case _ => false
    }

    override def hashCode(): Int = 17  * txId.hash
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
  
  implicit class AckConfirmTxTo(t: AckConfirmTx) extends ToBytes[AckConfirmTx] {
    override def toBytes: Array[Byte] = AckConfirmTxSerializer.toBytes(t)
  }
  implicit class AckConfirmTxFrom(b: Array[Byte]) {
    def toAckConfirmTx: AckConfirmTx = AckConfirmTxSerializer.fromBytes(b)
  }

  implicit class ConfirmTxTo(t: ConfirmTx) extends ToBytes[ConfirmTx] {
    override def toBytes: Array[Byte] = ConfirmTxSerializer.toBytes(t)
  }
  implicit class ConfirmTxFrom(b: Array[Byte]) {
    def toConfirmTx: ConfirmTx = ConfirmTxSerializer.fromBytes(b)
  }
}
