package sss.asado.network

import java.net.{InetAddress, InetSocketAddress}

import akka.actor._
import akka.agent.Agent
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Random, Try}

/**
  * Control all network interaction
  * must be singleton
  */
trait BindControllerSettings {

  val applicationName: String
  val bindAddress: String = "0.0.0.0"
  val port: Int = 8084
  val declaredAddressOpt: Option[String]
  val connectionTimeout: Int = 60
  val localOnly: Boolean = false
  val connectionRetryIntervalSecs: Int
  val appVersion: String
}

class NetworkController(messageRouter: ActorRef, settings: BindControllerSettings, upnp: Option[UPnP], peerList: Agent[Set[ConnectedPeer]]) extends Actor with ActorLogging {

  import NetworkController._
  import context.system

  val nodeNonce: Int = Random.nextInt()
  val handshakeTemplate = Handshake(settings.applicationName,
    ApplicationVersion(settings.appVersion),
    ownSocketAddress.getAddress.toString,
    ownSocketAddress.getPort,
    nodeNonce,
    0
  )

  //check own declared address for validity
  require(NetworkUtil.isAddressValid(settings.declaredAddressOpt), upnp)

  lazy val externalSocketAddress = settings.declaredAddressOpt
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

  private def manageNetwork: Receive = {

    case b@Bound(localAddr) =>
      log.info("Successfully bound to the port " + settings.port)

    case CommandFailed(_: Bind) =>
      log.error("Network port " + settings.port + " already in use!")
      context stop self

    case ConnectTo(remote) =>
      peerList().find(_.address == remote) match {
      case None => IO(Tcp) ! Connect(remote, localAddress = None, timeout = connTimeout, pullMode = true)
      case Some(p) => log.debug(s"already connected to $p")
    }

    case c@Connected(remote, local) =>
      val connection = sender()
      val handler = createConnectionHandler(connection, remote, nodeNonce)
      context watch handler
      connection ! Register(handler, keepOpenOnPeerClosed = false, useResumeWriting = true)
      handler ! handshakeTemplate.copy(time = System.currentTimeMillis() / 1000)


    case Terminated(ref) =>
      log.debug(s"We are disconnected from  $ref")

      peerList().find(_.handlerRef == ref) map { found =>
        peerList.alter(_.filterNot(_ == found)) map { _ =>
          self ! ConnectTo(found.address)
        }
      }

    case p@ConnectedPeer(addr, hndlr) =>
      log.debug(s"We are connected to  $p")
      peerList.send(_ + p)


    case ShutdownNetwork =>
      log.info("Going to shutdown all connections & unbind port")
      self ! Unbind
      context stop self

    case CommandFailed(c: Connect) =>
      log.info(s"Failed to connect to : ${c.remoteAddress}")
      //context.system.scheduler.scheduleOnce(settings.connectionRetryIntervalSecs.seconds, self,  ConnectTo(c.remoteAddress))

    case CommandFailed(cmd: Tcp.Command) =>
      log.info(s"Failed to execute command : $cmd")

    case SendToNetwork(nm, strategy) => strategy(peerList()) foreach(cp => cp.handlerRef ! nm)

    case e @ NetworkMessage(msgCode, bytes) =>
      // allow receiver to reply directly to connection
      // but route through here to avoid coupling message router with connection handler
      messageRouter forward e


    case nonsense: Any =>
      log.warning(s"NetworkController: got something strange $nonsense")
  }

  def createConnectionHandler(connection: ActorRef, remoteAddress: InetSocketAddress, nodeNonce:Int): ActorRef = {
    context.actorOf(Props(classOf[ConnectionHandler], connection, remoteAddress, nodeNonce.toLong))
  }

  override def receive: Receive = manageNetwork

  override def postStop = log.warning("Network controller is down!"); super.postStop
}

object NetworkController {

  case class SendToNetwork(msg: NetworkMessage, strategy: (Set[ConnectedPeer]) => Set[ConnectedPeer] = (s) => s)
  case object ShutdownNetwork
  case class ConnectTo(address: InetSocketAddress)

}


