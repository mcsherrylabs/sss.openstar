package sss.asado

import sss.asado.util.hash.SecureCryptographicHash
import sss.asado.util.Serialize._


package object quorumledger {

  object QuorumLedgerException { def apply(msg: String) = throw new QuorumLedgerException(msg) }
  class QuorumLedgerException(msg: String) extends Exception(msg)


  trait QuorumLedgerTx {

    lazy val txId: Array[Byte] = SecureCryptographicHash.hash(this.toBytes)

    private[quorumledger] val uniqueMessage = System.nanoTime()
  }

  private[quorumledger] val AddNodeCode = 1.toByte
  private[quorumledger] val RemoveNodeCode = 2.toByte

  case class AddNodeId(nodeId: String) extends QuorumLedgerTx
  case class RemoveNodeId(nodeId: String) extends QuorumLedgerTx


  implicit class QuorumLedgerToBytes(val tx: QuorumLedgerTx) extends AnyVal {

    def toBytes: Array[Byte] = tx match {
      case e: AddNodeId => e.toBytes
      case e: RemoveNodeId => e.toBytes
    }
  }

  implicit class QuorumLedgerFromBytes(val bytes: Array[Byte]) extends AnyVal {

    def toQuorumLedgerTx: QuorumLedgerTx = bytes.head match {
      case AddNodeCode => bytes.toAddNode
      case RemoveNodeCode => bytes.toRemoveNode
      case x => QuorumLedgerException(s"Code $x is not a known tx code.")
    }
  }


  implicit class RemoveNodeFromBytes(bytes: Array[Byte]) {
    def toRemoveNode: RemoveNodeId = {
      val extracted = bytes.extract(ByteDeSerialize, StringDeSerialize, LongDeSerialize)
      require(extracted._1 == RemoveNodeCode,
        s"Wrong leading byte for RemoveNodeCode ${bytes.head} instead of $RemoveNodeCode")
      new RemoveNodeId(extracted._2) {
        private[quorumledger] override val uniqueMessage: Long = extracted._3
      }
    }
  }

  implicit class RemoveNodeToBytes(remove: RemoveNodeId) {
    def toBytes: Array[Byte] = {
      ByteSerializer(RemoveNodeCode) ++
        StringSerializer(remove.nodeId) ++
        LongSerializer(remove.uniqueMessage)
          .toBytes
    }
  }

  implicit class AddNodeFromBytes(bytes : Array[Byte]) {
    def toAddNode: AddNodeId = {
      val extracted = bytes.extract(ByteDeSerialize, StringDeSerialize, LongDeSerialize)
      require(extracted._1 == AddNodeCode,
        s"Wrong leading byte for AddNodeCode ${bytes.head} instead of $AddNodeCode")
      new AddNodeId(extracted._2) {
        private[quorumledger] override val uniqueMessage: Long = extracted._3
      }
    }
  }

  implicit class AddNodeToBytes(add: AddNodeId) {
    def toBytes: Array[Byte] = {
      ByteSerializer(AddNodeCode) ++
        StringSerializer(add.nodeId) ++
        LongSerializer(add.uniqueMessage)
          .toBytes
    }
  }

}
