package sss.asado.network

import java.net.InetSocketAddress

import akka.actor.{Actor, _}
import akka.agent.Agent
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import sss.asado.network.NetworkController._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

/**
  * Control all network interaction
  * must be singleton
  */

object NetworkController {

  case object StartNetwork
  case object QuorumGained
  case object QuorumLost

  def quorum(numInPeersList : Int): Int = if (numInPeersList == 0) 0 else ((numInPeersList+1) / 2)

  trait BindControllerSettings {

    val applicationName: String
    val bindAddress: String = "0.0.0.0"
    val maxNumConnections: Int = 500
    val port: Int = 8084
    val declaredAddressOpt: Option[String]
    val connectionTimeout: Int = 60
    val localOnly: Boolean = false
    val connectionRetryIntervalSecs: Int
    val appVersion: String
  }


  case class SendToNetwork(msg: NetworkMessage, strategy: (Set[Connection]) => Set[Connection] = (s) => s)
  case class SendToNodeId(msg: NetworkMessage, nId: NodeId)
  case class ConnectTo(nId: NodeId)

  case class PeerConnectionLost(conn: Connection, updatedSet: Set[Connection])

  case class PeerConnectionGained(conn: Connection, updatedSet: Set[Connection])
  case class ConnectionGained(conn: Connection, updatedSet: Set[Connection])
  case class ConnectionLost(conn: Connection,updatedSet: Set[Connection])

  case object ShutdownNetwork

  private val peerPattern = """(.*):(.*):(\d\d\d\d)""".r
  def toNodeId(pattern: String): NodeId = pattern match { case peerPattern(id, ip, port) => NodeId(id, new InetSocketAddress(ip, port.toInt))}
  def toNodeIds(patterns: Set[String]): Set[NodeId] = patterns map (toNodeId)
}

class NetworkController(messageRouter: ActorRef,
                             netInf: NetworkInterface,
                             peersList: Set[NodeId],
                             peers: Agent[Set[Connection]],
                             clientConnnections: Agent[Set[Connection]],
                             stateController: ActorRef) extends Actor with ActorLogging {


  private val quorum: Int = NetworkController.quorum(peersList.size)

  import context.system

  IO(Tcp) ! Bind(self, netInf.localAddress)

  private def manageNetwork: Actor.Receive = {

    case b@Bound(localAddr) =>
      log.info("Successfully bound to " + localAddr)

    case b@Unbound => log.info("Successfully unbound")


    case CommandFailed(b: Bind) =>
      log.error(s"Network port ${b.localAddress} already in use!?")

    case SendToNetwork(nm, strategy) =>
      strategy(peers.get) foreach (cp => cp.handlerRef ! nm)

    case SendToNodeId(nm, NodeId(nodeId, addr)) =>
      peers.get.find (conn => conn.nodeId.id == nodeId) match {
        case Some(conn) => conn.handlerRef ! nm
        case None => clientConnnections.get find (conn => conn.nodeId.id == nodeId) map (_.handlerRef ! nm)
      }

    case ConnectTo(NodeId(nodeId, addr)) =>
      peersList.find(_.id == nodeId) match {
        case Some(peer) => peers().find(_.nodeId.id == nodeId) match {
          case None => IO(Tcp) ! Connect(addr, localAddress = None, timeout = Option(netInf.connTimeout), pullMode = true)
          case Some(p) => log.info(s"Already connected to $p, will not attepmt to connect.")
        }
        case None => IO(Tcp) ! Connect(addr, localAddress = None, timeout = Option(netInf.connTimeout), pullMode = true)
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
        case None =>
          clientConnnections().find(_.nodeId.id == nodeId) match {
            case None =>
              clientConnnections.alter(_ + p) map (stateController ! ConnectionGained(p, _))
            case Some(alreadyConnected) =>
              log.info(s"Client Connection $alreadyConnected exists, closing this one.")
              sender() ! CloseConnection
          }
      }

    case t@Terminated(ref) =>

      peers().find(_.handlerRef == ref) match {
        case Some(found) =>
          peers.alter(_.filterNot(_.nodeId.id == found.nodeId.id)) map { conns =>
            if (conns.size + 1 == quorum) stateController ! QuorumLost
            else stateController ! PeerConnectionLost(found, conns)
            self ! ConnectTo(found.nodeId)
          }
        case None =>
          clientConnnections().find(_.handlerRef == ref) map { found =>
            clientConnnections.alter(_.filterNot(_.nodeId.id == found.nodeId.id)) map { conns =>
              stateController ! ConnectionLost(found, conns)
            }
          }
      }

    case c@Connected(remote, local) =>
      val connection = sender()
      if(clientConnnections().size < netInf.maxNumConnections) {
        val connectionNonce = Random.nextLong
        val handler = createConnectionHandler(connectionNonce, connection, remote, netInf)
        context watch handler
        connection ! Register(handler, keepOpenOnPeerClosed = false, useResumeWriting = true)
        handler ! netInf.createHandshake(connectionNonce)
      } else {
        log.warning(s"Max client connections reached (${netInf.maxNumConnections}, closing...")
        connection ! Close
      }


    case ShutdownNetwork =>
      log.info("Going to shutdown all connections & unbind port")
      self ! Unbind
      context stop self

    case cf@CommandFailed(c: Connect) =>
      log.info(s"Failed to connect to $c, retry in ${netInf.connectionRetryInterval}")
      peersList.find(_.inetSocketAddress == c.remoteAddress) map { found =>
        context.system.scheduler.scheduleOnce(netInf.connectionRetryInterval, self, ConnectTo(found))
      }

    case CommandFailed(cmd: Tcp.Command) => log.info(s"Failed to execute command : $cmd")

    case e: IncomingNetworkMessage =>
      // allow receiver to reply directly to connection
      // but route through here to avoid coupling message router with connection handler
      messageRouter forward e

    case StartNetwork =>
      peersList foreach (self ! ConnectTo(_))

    case nonsense => log.warning(s"NetworkController: unhandled msg $nonsense")
  }


  def createConnectionHandler(nonce: Long, connection: ActorRef, remoteAddress: InetSocketAddress, netInf: NetworkInterface): ActorRef = {
    context.actorOf(Props(classOf[ConnectionHandler], nonce, connection, remoteAddress, netInf))
  }

  override def postStop = log.warning("Network controller is down!"); super.postStop
  override def receive: Receive = manageNetwork

}
