package sss.openstar.common.telemetry.serialize

import sss.openstar.common.block.{BlockId, BlockIdFrom, BlockIdTo}
import sss.openstar.common.telemetry.Report
import sss.openstar.util.Serialize._
import sss.openstar.util.SeqSerializer._

object ReportSerializer extends Serializer[Report]  {

  override def toBytes(report: Report): Array[Byte] = {
    StringSerializer(report.nodeName) ++
    IntSerializer(report.reportIntervalSeconds) ++
    OptionSerializer[BlockId](report.lastBlock, bId => ByteArraySerializer(bId.toBytes)) ++
      IntSerializer(report.numConnections) ++
      report.connections.map(StringSerializer).toBytes
  }

  override def fromBytes(b: Array[Byte]): Report = {

    Report.tupled(b.extract(
      StringDeSerialize,
      IntDeSerialize,
      OptionDeSerialize(
        ByteArrayDeSerialize(
          BlockIdFrom(_).toBlockId
        )
      ),
      IntDeSerialize,
      SequenceDeSerialize(_.extract(StringDeSerialize))
    ))

  }
}
