package sss.asado.network

import java.net.InetSocketAddress

import akka.actor.{Actor, _}
import akka.agent.Agent
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import sss.asado.network.NetworkController._
import sss.asado.state.AsadoStateProtocol.{QuorumGained, QuorumLost}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Control all network interaction
  * must be singleton
  */

object NetworkController {

  def quorum(numInPeersList : Int): Int = if (numInPeersList == 0) 0 else (numInPeersList / 2)

  trait BindControllerSettings {

    val applicationName: String
    val bindAddress: String = "0.0.0.0"
    val nodeId : String
    val port: Int = 8084
    val declaredAddressOpt: Option[String]
    val connectionTimeout: Int = 60
    val localOnly: Boolean = false
    val connectionRetryIntervalSecs: Int
    val appVersion: String
  }


  case class SendToNetwork(msg: NetworkMessage, strategy: (Set[Connection]) => Set[Connection] = (s) => s)

  case class ConnectTo(nId: NodeId)

  case class PeerConnectionLost(conn: Connection, updatedSet: Set[Connection])

  case class PeerConnectionGained(conn: Connection, updatedSet: Set[Connection])
  case class ConnectionGained(conn: Connection)
  case object ConnectionLost
  //case class QuorumLost(conn: Connection, updatedSet: Set[Connection])
  //case class QuorumGained(conn: Connection, updatedSet: Set[Connection])

  case object ShutdownNetwork

  private val peerPattern = """(.*):(.*):(\d\d\d\d)""".r
  def toInetSocketAddress(pattern: String): NodeId = pattern match { case peerPattern(id, ip, port) => NodeId(id, new InetSocketAddress(ip, port.toInt))}
  def toInetSocketAddresses(patterns: Set[String]): Set[NodeId] = patterns map (toInetSocketAddress)
}

class NetworkController(messageRouter: ActorRef,
                             netInf: NetworkInterface,
                             peersList: Set[NodeId],
                             peers: Agent[Set[Connection]],
                             stateController: ActorRef) extends Actor with ActorLogging {


  private val quorum: Int = NetworkController.quorum(peersList.size)

  import context.system

  IO(Tcp) ! Bind(self, netInf.localAddress)

  peersList foreach (self ! ConnectTo(_))

  private def manageNetwork: Actor.Receive = {

    case b@Bound(localAddr) =>
      log.info("Successfully bound to " + localAddr)

    case b@Unbound => log.info("Successfully unbound")


    case CommandFailed(b: Bind) =>
      log.error(s"Network port ${b.localAddress} already in use!?")

    case SendToNetwork(nm, strategy) =>
      strategy(peers.get) foreach (cp => cp.handlerRef ! nm)

    case ConnectTo(nodeId) =>
      peersList.find(_.id == nodeId.id) match {
        case Some(peer) => peers().find(_.nodeId == nodeId.id) match {
          case None => IO(Tcp) ! Connect(nodeId.inetSocketAddress, localAddress = None, timeout = Option(netInf.connTimeout), pullMode = true)
          case Some(p) => log.info(s"Already connected to $p, will not attepmt to connect.")
        }
        case None => IO(Tcp) ! Connect(nodeId.inetSocketAddress, localAddress = None, timeout = Option(netInf.connTimeout), pullMode = true)
      }


    case p@Connection(NodeId(nodeId, addr), hndlr) =>

      peersList.find(_.id == nodeId) match {
        case Some(peer) => peers().find(_.nodeId.id == nodeId) match {
          case None => peers.alter(_ + p) map (conns => {
            if (conns.size == quorum) stateController ! QuorumGained
            else if (conns.size > quorum) stateController ! PeerConnectionGained(p, conns)
          })
          case Some(alreadyInPeers) => {
            log.info(s"Connection $alreadyInPeers exists, closing this one.")
            sender() ! CloseConnection
          }
        }
        case None => stateController ! ConnectionGained(p)
      }

    case t@Terminated(ref) =>

      peers().find(_.handlerRef == ref) match {
        case Some(found) =>
          peers.alter(_.filterNot(_ == found)) map { conns =>
            if (conns.size + 1 == quorum) stateController ! QuorumLost
            else if (conns.size < quorum) stateController ! PeerConnectionLost(found, conns)
            self ! ConnectTo(found.nodeId)
          }
        case None => stateController ! ConnectionLost
      }

    case c@Connected(remote, local) =>
      val connection = sender()
      val handler = createConnectionHandler(connection, remote, netInf.nodeNonce)
      context watch handler
      connection ! Register(handler, keepOpenOnPeerClosed = false, useResumeWriting = true)
      handler ! netInf.createHandshake


    case ShutdownNetwork =>
      log.info("Going to shutdown all connections & unbind port")
      self ! Unbind
      context stop self

    case cf@CommandFailed(c: Connect) =>
      peersList.find(_.inetSocketAddress == c.remoteAddress) map { found =>
        context.system.scheduler.scheduleOnce(netInf.connectionRetryInterval, self, ConnectTo(found))
      }

    case CommandFailed(cmd: Tcp.Command) => log.info(s"Failed to execute command : $cmd")

    case e@NetworkMessage(msgCode, bytes) =>
      // allow receiver to reply directly to connection
      // but route through here to avoid coupling message router with connection handler
      messageRouter forward e

    case nonsense => log.warning(s"NetworkController: unhandled msg $nonsense")
  }


  def createConnectionHandler(connection: ActorRef, remoteAddress: InetSocketAddress, nodeNonce:Int): ActorRef = {
    context.actorOf(Props(classOf[ConnectionHandler], connection, remoteAddress, nodeNonce.toLong))
  }

  override def postStop = log.warning("Network controller is down!"); super.postStop
  override def receive: Receive = manageNetwork
}
