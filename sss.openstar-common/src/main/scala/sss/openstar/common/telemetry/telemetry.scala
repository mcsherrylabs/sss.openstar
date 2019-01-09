package sss.openstar.common

import akka.util.ByteString
import sss.openstar.common.block.BlockId
import sss.openstar.common.telemetry.serialize.ReportSerializer
import sss.openstar.util.Serialize.ToBytes

package object telemetry {

  case class Report(nodeName: String,
                    lastBlock: Option[BlockId],
                    numConnections: Int,
                    connections: Seq[String]
                   )

  implicit class ReportToBytes(report:Report) extends ToBytes {
    override def toBytes: Array[Byte] = ReportSerializer.toBytes(report)
    def toByteString: ByteString = ByteString(ReportSerializer.toBytes(report))
  }

  implicit class ReportFromByteString(bs:ByteString)  {
    def toReport: Report = ReportSerializer.fromBytes(bs.toArray)
  }

  implicit class ReportFromBytes(bs:Array[Byte])  {
    def toReport: Report = ReportSerializer.fromBytes(bs)
  }
}
