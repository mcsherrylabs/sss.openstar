package sss.openstar.telemetry

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import sss.ancillary.Logging
import sss.openstar.OpenstarEvent
import sss.openstar.block.BlockChainLedger.NewBlockId
import sss.openstar.network.MessageEventBus
import sss.openstar.network.{Connection, ConnectionLost}
import sss.openstar.telemetry.Client.{Report, ReportInterval}
import sss.openstar.util.Serialize._

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

object Client {
  case class Report(lastBlock: Long, numConnections: Int)
  case class ReportInterval(intervalSeconds: Long) extends OpenstarEvent
}

class Client(telemetryUrl: String = "wss://localhost:8123/telemetry")(implicit as: ActorSystem,
                                                                      am :ActorMaterializer,
                                                                      events: MessageEventBus) extends Logging {

  def report(r: Report): Try[Unit] = Try {
    val requestSource: Source[Message, NotUsed] = Source.single(
      BinaryMessage(r.toByteString)
    )

    val flow = createFlow(requestSource)

    val (upgradeResponse, closed) =  Http()
      .singleWebSocketRequest(WebSocketRequest(telemetryUrl), flow, connectionContext = httpsContxt)


    val connected = upgradeResponse.map { upgrade =>
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    // in a real application you would not side effect here
    // and handle errors more carefully
    connected.onComplete(println)
    closed.foreach(_ => println("closed"))
  }

  val requestSink: Sink[Message, Future[Done]] = Sink.foreach {

    case message: BinaryMessage =>
      message.toStrict(3.seconds) map { strictMsg =>
        val newReportSleepInterval = strictMsg.data.toArray.extract(LongDeSerialize)
        events publish ReportInterval(newReportSleepInterval)
      }
    case message: Message =>
      log.debug(s"Unexpected received: ${message.asTextMessage}")
  }

  // the Future[Done] is the materialized value of Sink.foreach
  // and it is completed when the stream completes

  def createFlow(requestSource: Source[Message, NotUsed]): Flow[Message, Message, Future[Done]] = {
    Flow.fromSinkAndSourceMat(requestSink, requestSource)(Keep.left)
  }

  // upgradeResponse is a Future[WebSocketUpgradeResponse] that
  // completes or fails when the connection succeeds or fails
  // and closed is a Future[Done] representing the stream completion from above

  val sslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose.withDisableHostnameVerification(true)))
  val httpsContxt = Http().createClientHttpsContext(sslConfig)

}

