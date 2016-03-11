package sss.asado.network

import java.net.{InetAddress, InetSocketAddress, NetworkInterface, URI}

import akka.actor._
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.Timeout
import sss.ancillary.Logging

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

/**
  * Control all network interaction
  * must be singleton
  */
trait BindControllerSettings {
  implicit val timeout : Timeout
  val applicationName: String
  val nodeNonce: Int
  val bindAddress: String = "0.0.0.0"
  val port: Int = 8084
  val declaredAddress: Option[String]
  val connectionTimeout: Int = 60
  val localOnly = false
  val appVersion: ApplicationVersion
}

class NetworkController(messageRouter: ActorRef, settings: BindControllerSettings, upnp: Option[UPnP] = None) extends Actor with Logging {

  import NetworkController._
  import context.system

  val handshakeTemplate = Handshake(settings.applicationName,
    settings.appVersion,
    ownSocketAddress.getAddress.toString,
    ownSocketAddress.getPort,
    settings.nodeNonce,
    0
  )

  //check own declared address for validity
  require(NetworkUtil.isAddressValid(settings.declaredAddress), upnp)

  lazy val externalSocketAddress = settings.declaredAddress
    .flatMap(s => Try(InetAddress.getByName(s)).toOption)
    .orElse {
      if (upnp.isDefined) upnp.get.externalAddress else None
    }.map(ia => new InetSocketAddress(ia, settings.port))

  //an address to send to peers
  lazy val ownSocketAddress = externalSocketAddress.getOrElse(localAddress)

  log.info(s"Declared address: $ownSocketAddress")

  lazy val localAddress = new InetSocketAddress(InetAddress.getByName(settings.bindAddress), settings.port)
  lazy val connTimeout = Some(new FiniteDuration(settings.connectionTimeout, SECONDS))

  IO(Tcp) ! Bind(self, localAddress)

  private def manageNetwork(connected: Set[ConnectedPeer]): Receive = {

    case b@Bound(localAddr) =>
      log.info("Successfully bound to the port " + settings.port)

    case CommandFailed(_: Bind) =>
      log.error("Network port " + settings.port + " already in use!")
      context stop self

    case ConnectTo(remote) =>
      IO(Tcp) ! Connect(remote, localAddress = None, timeout = connTimeout, pullMode = true)

    case c@Connected(remote, local) =>
      val connection = sender()
      val handler = createConnectionHandler(connection, remote, settings.nodeNonce)
      context watch handler
      connection ! Register(handler, keepOpenOnPeerClosed = false, useResumeWriting = true)
      handler ! handshakeTemplate.copy(time = System.currentTimeMillis() / 1000)


    case Terminated(ref) =>
      log.debug(s"We are disconnected from  $ref")
      connected.find(_.handlerRef == ref).map { cp =>
        messageRouter ! LostConnection(cp.address)
        context.become(manageNetwork(connected - cp))
      }


    case p@ConnectedPeer(addr, hndlr) => {
      log.debug(s"We are connected to  $p")
      messageRouter ! NewConnection(addr)
      context.become(manageNetwork(connected + p))
    }

    case ShutdownNetwork =>
      log.info("Going to shutdown all connections & unbind port")
      self ! Unbind
      context stop self

    case CommandFailed(c: Connect) =>
      log.info(s"Failed to connect to : ${c.remoteAddress}")

    case CommandFailed(cmd: Tcp.Command) =>
      log.info(s"Failed to execute command : $cmd")

    case SendToNetwork(nm, strategy) => strategy(connected) foreach(cp => cp.handlerRef ! nm)

    case e @ NetworkMessage(msgCode, bytes) => {
      // allow receiver to reply directly to connection
      // but route through here to avoid coupling message router with connection handler
      messageRouter forward e
    }

    case nonsense: Any =>
      log.warn(s"NetworkController: got something strange $nonsense")
  }

  def createConnectionHandler(connection: ActorRef, remoteAddress: InetSocketAddress, nodeNonce:Int): ActorRef = {
    context.actorOf(Props(classOf[ConnectionHandler], connection, remoteAddress, nodeNonce.toLong))
  }

  override def receive: Receive = manageNetwork(Set.empty)
}

object NetworkController {

  case class SendToNetwork(msg: NetworkMessage, strategy: (Set[ConnectedPeer]) => Set[ConnectedPeer] = (s) => s)
  case object ShutdownNetwork
  case class ConnectTo(address: InetSocketAddress)

}

object NetworkUtil extends Logging {

  def isAddressValid(declaredAddress: Option[String], upnpOpt: Option[UPnP] = None): Boolean = {
    //check own declared address for validity

    declaredAddress.map { myAddress =>
      Try {
        val uri = new URI("http://" + myAddress)
        val myHost = uri.getHost
        val myAddrs = InetAddress.getAllByName(myHost)

        NetworkInterface.getNetworkInterfaces.exists { intf =>
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

