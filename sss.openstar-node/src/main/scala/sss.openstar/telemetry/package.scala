package sss.openstar

import akka.util.ByteString
import sss.openstar.telemetry.Client.Report
import sss.openstar.telemetry.serialize.ReportSerializer
import sss.openstar.util.Serialize.ToBytes

package object telemetry {

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
