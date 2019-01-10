package sss.telemetry.server

import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import sss.ancillary.Logging
import sss.openstar.common.block.BlockId
import sss.openstar.common.telemetry._
import sss.openstar.util.Serialize.IntSerializer


object TelemetryRoute extends Logging {

  def reportToString(r:Report): String = {
    def blockIdtoString(l: Option[BlockId]): String = {
      l match {
        case None => "No last block"
        case Some(bId) => s"${bId.blockHeight.toString.padTo(6, " ").mkString},${bId.txIndex.toString.padTo(6, " ").mkString}"
      }
    }

    s"${r.nodeName.padTo(20, " ").mkString} " +
      s"${r.reportIntervalSeconds.toString.padTo(5, " ").mkString} " +
      s"${blockIdtoString(r.lastBlock)} " +
      s"${r.numConnections.toString.padTo(3, " ").mkString} " +
      s"${r.connections.mkString(",")}"
  }

  def logReport(r:Report): Report = {
    log.info(reportToString(r))
    r
  }

  def updateReportInterval(currentIntervalSeconds: Int): Int = currentIntervalSeconds

  private val flow = Flow[Message]
    .collect {

      case bm: BinaryMessage.Strict =>
        // consume the stream
        BinaryMessage(
          ByteString(
            IntSerializer(
              updateReportInterval(
                logReport (
                  bm.data.toReport
                ).reportIntervalSeconds
              )
            ).toBytes
          )
        )

      case o =>
        log.debug(s"Client sending non strict binary or text messages ${o.asTextMessage.getStrictText.take(20)} ... ")
        BinaryMessage(
          ByteString(
            IntSerializer(Int.MaxValue).toBytes
          )
        )

    }



  val route =
    path("telemetry") {
      handleWebSocketMessages(flow)
    }

}

