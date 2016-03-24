
import akka.actor.ActorRef
import ledger.{SignedTx, TxId}
import sss.asado.block.serialize.{AckConfirmTxSerializer, ConfirmTxSerializer}
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.Serialize.ToBytes

/**
  * Created by alan on 3/24/16.
  */
package object block {

  case class DistributeTx(client: ActorRef, signedTx: SignedTx, height: Long, id: Long)
  case class ConfirmTx(stx: SignedTx, height: Long, id: Long)
  case class AckConfirmTx(txId: TxId, height: Long, id: Long) extends ByteArrayComparisonOps {
    override def equals(obj: scala.Any): Boolean = obj match {
      case ackConfirm: AckConfirmTx => ackConfirm.height == height &&
        ackConfirm.id == id &&
        ackConfirm.txId.isSame(txId)

      case _ => false
    }

    override def hashCode(): Int = (17 + id.toInt) * txId.hash
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
