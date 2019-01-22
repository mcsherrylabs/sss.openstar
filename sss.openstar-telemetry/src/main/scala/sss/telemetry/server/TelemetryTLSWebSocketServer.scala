package sss.telemetry.server

import java.io.{File, FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class TelemetryTLSWebSocketServer(
                       keyStorePassword: String,
                       keyStorePath: String,
                       bindToIp: String,
                       bindToPort: Int,
                       keyStoreType: String = "PKCS12",
                       keyFactoryType:String = "SunX509",
                     )(implicit as: ActorSystem) {

  implicit val am = ActorMaterializer()

  val password: Array[Char] = keyStorePassword.toCharArray // do not store passwords in code, read them from somewhere safe!

  val ks: KeyStore = KeyStore.getInstance(keyStoreType)

  val keystore: InputStream = {
    val attempt1 = new FileInputStream(new File(keyStorePath))
    Option(attempt1).getOrElse {
      val attempt2 = getClass.getClassLoader.getResourceAsStream(keyStorePath)
      Option(attempt2).getOrElse(
        throw new IllegalArgumentException(s"Cannot load keystore $keyStorePath")
      )
    }
  }

  ks.load(keystore, password)

  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance(keyFactoryType) // "SunX509"
  keyManagerFactory.init(ks, password)

  val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(keyFactoryType) // "SunX509"
  tmf.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
  val https: HttpsConnectionContext = ConnectionContext.https(sslContext)

  Http()
    .bindAndHandle(TelemetryRoute.route, bindToIp, bindToPort, connectionContext = https)
    .onComplete {
      case Success(value) => println(value)
      case Failure(err) => println(err)
    }

}


