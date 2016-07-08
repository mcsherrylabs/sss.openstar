package sss.asado.network

import java.net.{InetAddress, InetSocketAddress, URI, NetworkInterface => JNetworkInterface}

import com.google.common.primitives.Longs
import sss.ancillary.Logging
import sss.asado.network.NetworkController.BindControllerSettings

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Random, Try}

/**
  * Created by alan on 3/31/16.
  */

trait IdentityVerification {
  val nodeId: String
  val tag: String
  def sign(msg: Array[Byte]): Array[Byte]
  def verify(sig: Array[Byte], msg: Array[Byte], nodeId: String, tag:String): Boolean
}

class NetworkInterface(private[network] val handshakeVerifier: IdentityVerification,
                       settings: BindControllerSettings,
                       upnp: Option[UPnP]) {

  def createHandshake(nonce: Long, mySig: Array[Byte] = Array()): Handshake = {
    handshakeTemplate.copy(
      fromNonce = nonce,
      sig = mySig,
      time = System.currentTimeMillis() / 1000)
  }

  val connTimeout = new FiniteDuration(settings.connectionTimeout, SECONDS)
  private lazy val handshakeTemplate = Handshake(settings.applicationName,
    ApplicationVersion(settings.appVersion),
    ownSocketAddress.getAddress.toString,
    handshakeVerifier.nodeId,
    handshakeVerifier.tag,
    ownSocketAddress.getPort,
    Random.nextLong,
    Array(),
    0
  )

  val maxNumConnections = settings.maxNumConnections

  val connectionRetryInterval =  new FiniteDuration(settings.connectionRetryIntervalSecs, SECONDS)
  //check own declared address for validity
  require(NetworkInterface.isAddressValid(settings.declaredAddressOpt), upnp)

  private lazy val externalSocketAddress = settings.declaredAddressOpt
    .flatMap(s => Try(InetAddress.getByName(s)).toOption)
    .orElse {
      if (upnp.isDefined) upnp.get.externalAddress else None
    }.map(ia => new InetSocketAddress(ia, settings.port))
  //an address to send to peers
  private lazy val ownSocketAddress = externalSocketAddress.getOrElse(localAddress)
  lazy val localAddress = new InetSocketAddress(InetAddress.getByName(settings.bindAddress), settings.port)

}

object NetworkInterface extends Logging {

  def isAddressValid(declaredAddress: Option[String], upnpOpt: Option[UPnP] = None): Boolean = {
    //check own declared address for validity

    declaredAddress.map { myAddress =>
      Try {
        val uri = new URI("http://" + myAddress)
        val myHost = uri.getHost
        val myAddrs = InetAddress.getAllByName(myHost)

        JNetworkInterface.getNetworkInterfaces.exists { intf =>
          intf.getInterfaceAddresses.exists { intfAddr =>
            val extAddr = intfAddr.getAddress
            myAddrs.contains(extAddr)
          }
        } match {
          case true => true
          case false => upnpOpt.map { upnp =>
            val extAddr = upnp.externalAddress
            myAddrs.contains(extAddr)
          }.getOrElse(false)
        }
      }.recover { case t: Throwable =>
        log.error("Declared address validation failed: ", t)
        false
      }.getOrElse(false)
    }.getOrElse(true).ensuring(_ == true, "Declared address isn't valid")
  }

}
