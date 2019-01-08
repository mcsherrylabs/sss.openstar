package sss.telemetry.server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{complete, handleWebSocketMessages, onComplete, path, pathEndOrSingleSlash, _}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import java.io.{File, FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Server extends App {

  implicit val as = ActorSystem("example")
  implicit val am = ActorMaterializer()

  val password: Array[Char] = "password".toCharArray // do not store passwords in code, read them from somewhere safe!

  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystore: InputStream = new FileInputStream(new File("src/main/resources/keystore.jks"))
  // getClass.getClassLoader.getResourceAsStream("src/main/resources/keystore")

  require(keystore != null, "Keystore required!")
  ks.load(keystore, password)

  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)

  val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  tmf.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
  val https: HttpsConnectionContext = ConnectionContext.https(sslContext)

  Http()
    .bindAndHandle(Route.websocketRoute, "0.0.0.0", 8123, connectionContext = https)
    .onComplete {
      case Success(value) => println(value)
      case Failure(err) => println(err)
    }

}

object Route {

  case object GetWebsocketFlow

  import Server.{as, am}

  val websocketRoute =
    pathEndOrSingleSlash {
      complete("WS server is alive\n")
    } ~ path("telemetry") {

      val handler = as.actorOf(Props(classOf[ClientHandlerActor], as, am))

      val futureFlow = (handler ? GetWebsocketFlow)(3.seconds).mapTo[Flow[Message, Message, _]]

      onComplete(futureFlow) {
        case Success(flow) => handleWebSocketMessages(flow)
        case Failure(err) => complete(err.toString)
      }

    }
}
