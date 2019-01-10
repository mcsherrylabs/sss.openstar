package sss.openstar.telemetry

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import sss.ancillary.Logging
import sss.openstar.OpenstarEvent
import sss.openstar.network.MessageEventBus
import sss.openstar.telemetry.Client.ClientResponse

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object Client {
  case class ClientResponse(byteStr: ByteString) extends OpenstarEvent
}

class Client(telemetryUrl: String, hostVerificationOff: Boolean)(implicit as: ActorSystem,
                                            events: MessageEventBus,
                                     ) extends Logging {

  private implicit val am :ActorMaterializer = ActorMaterializer()

  private val requestSink: Sink[Message, Future[Done]] = Sink.foreach {

    case message: BinaryMessage.Strict =>
        events publish ClientResponse(message.data)

    case message: BinaryMessage =>
      message.toStrict(3.seconds) map { strictMsg =>
        events publish ClientResponse(strictMsg.data)
      }

    case message: Message =>
      log.debug(s"Unexpected received: ${message.asTextMessage}")

  }

  private def createFlow(requestSource: Source[Message, NotUsed]): Flow[Message, Message, Future[Done]] = {
    Flow.fromSinkAndSourceMat(requestSink, requestSource)(Keep.left)
  }

  private val sslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose.withDisableHostnameVerification(hostVerificationOff)))
  private val httpsContxt = Http().createClientHttpsContext(sslConfig)


  def report(raw: ByteString): Try[Unit] = Try {

    val requestSource: Source[Message, NotUsed] =
      Source.single(
        BinaryMessage(raw)
      )

    val flow = createFlow(requestSource)

    val (upgradeResponse, closed) =  Http()
      .singleWebSocketRequest(WebSocketRequest(telemetryUrl), flow, connectionContext = httpsContxt)

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    connected.recover {
      case e =>
        log.warn(s"${e.toString}")
    }

    closed.recover {
      case NonFatal(e) =>
        log.warn(s"Closed failed ${e.toString}")
    }

  }

}

