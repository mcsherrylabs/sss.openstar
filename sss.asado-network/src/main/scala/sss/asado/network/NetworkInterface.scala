package sss.asado.network

import java.net.{
  InetAddress,
  InetSocketAddress,
  URI,
  NetworkInterface => JNetworkInterface
}

import sss.ancillary.Logging
import sss.asado.network.NetworkInterface.BindControllerSettings

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by alan on 3/31/16.
  */
class NetworkInterface(settings: BindControllerSettings, upnp: Option[UPnP]) {

  val appVersion = ApplicationVersion(settings.appVersion)
  val appName = settings.applicationName
  val handshakeTimeoutMs = settings.handshakeTimeoutMs
  val connTimeout = new FiniteDuration(settings.connectionTimeout, SECONDS)

  //check own declared address for validity
  require(NetworkInterface.isAddressValid(settings.declaredAddressOpt), upnp)

  private lazy val externalSocketAddress = settings.declaredAddressOpt
    .flatMap(s => Try(InetAddress.getByName(s)).toOption)
    .orElse(upnp.flatMap(_.externalAddress))
    .map(ia => new InetSocketAddress(ia, settings.port))

  //an address to send to peers
  private lazy val ownSocketAddress =
    externalSocketAddress.getOrElse(localAddress)
  lazy val localAddress = new InetSocketAddress(
    InetAddress.getByName(settings.bindAddress),
    settings.port)

}

object NetworkInterface extends Logging {

  trait BindControllerSettings {
    val applicationName: String
    val bindAddress: String = "0.0.0.0"
    val port: Int = 8084
    val declaredAddressOpt: Option[String]
    val handshakeTimeoutMs: Int = 5000
    val connectionTimeout: Int = 60
    val appVersion: String
  }

  def isAddressValid(declaredAddress: Option[String],
                     upnpOpt: Option[UPnP] = None): Boolean = {
    //check own declared address for validity

    declaredAddress
      .map { myAddress =>
        Try {
          val uri = new URI("http://" + myAddress)
          val myHost = uri.getHost
          val myAddrs = InetAddress.getAllByName(myHost)

          JNetworkInterface.getNetworkInterfaces.asScala.exists { intf =>
            intf.getInterfaceAddresses.asScala.exists { intfAddr =>
              val extAddr = intfAddr.getAddress
              myAddrs.contains(extAddr)
            }
          } match {
            case true => true
            case false =>
              upnpOpt
                .map { upnp =>
                  val extAddr = upnp.externalAddress
                  myAddrs.contains(extAddr)
                }
                .getOrElse(false)
          }
        }.recover {
            case t: Throwable =>
              log.error("Declared address validation failed: ", t)
              false
          }
          .getOrElse(false)
      }
      .getOrElse(true)
      .ensuring(_ == true, "Declared address isn't valid")
  }

}
