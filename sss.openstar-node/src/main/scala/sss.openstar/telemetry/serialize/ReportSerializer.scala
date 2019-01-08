package sss.openstar.telemetry.serialize

import sss.openstar.telemetry.Client.Report
import sss.openstar.util.Serialize._

object ReportSerializer extends Serializer[Report]  {

  override def toBytes(report: Report): Array[Byte] = {
    LongSerializer(report.lastBlock) ++
      IntSerializer(report.numConnections)
    .toBytes
  }

  override def fromBytes(b: Array[Byte]): Report = {
    Report
      .tupled(b.extract(
        LongDeSerialize,
        IntDeSerialize)
      )
  }
}
