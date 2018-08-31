package sss.asado.network

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, _}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import sss.asado.network.ConnectionHandler.{Begin, ConnectionRef, HandshakeStep}
import sss.asado.network.NetworkControllerActor._

import scala.concurrent.duration._
import scala.concurrent.Promise
import scala.util.{Failure, Random}
import language.postfixOps

private[network] object NetworkControllerActor {

  /*case class SendToNetwork(
      msg: NetworkMessage,
      strategy: Set[Connection] => Set[Connection] = s => s)*/

  case class SendToNodeId(msg: NetworkMessage, nId: NodeId)
  case class ConnectTo(nodeId: NodeId,
                       reconnectionStrategy: ReconnectionStrategy)

  case class UnBlackListAddr(inetAddress: InetAddress)
  case class UnBlackList(id: String)
  case class Disconnect(nodeId: NodeId)
  case class BlackListAddr(inetAddress: InetAddress, duration: Duration)
  case class BlackList(id: String, duration: Duration)
  case object ShutdownNetwork

}

private class NetworkControllerActor(netInf: NetworkInterface,
                                     initialStep: InitialHandshakeStepGenerator,
                                     startPromise: Promise[NetworkRef],
                                     messageEventBus: MessageEventBus,
                                     )
    extends Actor
    with ActorLogging {

  import context.system
  import context.dispatcher

  private val connectionsRef: AtomicReference[Set[Connection]]= new AtomicReference[Set[Connection]](Set())

  private val stopPromise: Promise[Unit] = Promise()
  private var connections = Set[ConnectionRef]()
  private var strategies = Map[NodeId, ReconnectionStrategy]()

  private var blackList = Map[InetAddress, Long]()
  private var blackListByIdentity = Map[String, Long]()

  IO(Tcp) ! Bind(self, netInf.localAddress)

  private def waitForBind: Actor.Receive = {
    case b @ Bound(localAddr) =>
      log.info("Successfully bound to " + localAddr)
      context.become(manageNetwork(sender()))
      startPromise.success(new NetworkRef(
        self,
        connectionsRef,
        stopPromise
      ))

  }

  private def manageNetwork(bindConnectionActor: ActorRef): Actor.Receive = {

    case b @ Unbound =>
      log.info("Successfully unbound, stopped the network.")
      context stop self
      stopPromise.success(())

    case c @ CommandFailed(b: Bind) =>
      stopPromise.complete(
        Failure(new RuntimeException(
          s"Network port ${b.localAddress} already in use? (Error:${c.cause})"))
      )

    case BlackList(id, duration) =>
      val banUntil = System.currentTimeMillis() + duration.toMillis
      blackListByIdentity += (id -> banUntil)

    case BlackListAddr(addr, duration) =>
      val banUntil = System.currentTimeMillis() + duration.toMillis
      blackList += (addr -> banUntil)

    case UnBlackListAddr(addr) =>
      blackList -= addr

    case UnBlackList(id) =>
      blackListByIdentity -= id

    case Disconnect(nId) =>
      disconnect(nId)

    case SendToNodeId(nm, NodeId(nodeId, _)) =>
      // TODO this *could* be a promise that is completed
      // TODO with failure if the nodeid is unknown or no such ActorRef.
      connections
        .find(_.nodeId.id == nodeId)
        .foreach(_.handlerRef ! nm)

    case c @ ConnectTo(n @ NodeId(nodeId, addr), reconnnectStrategy) =>
      if (!isConnected(n) &&
          !isBlackListed(n.address) &&
          !isBlackListed(nodeId)) {

        if (!reconnnectStrategy.isEmpty) {
          strategies += n -> reconnnectStrategy
        }

        log.debug(s"Attempting to IO connect to $addr}")

        IO(Tcp) ! Connect(addr,
                          localAddress = None,
                          timeout = Option(netInf.connTimeout),
                          pullMode = true)
      }

    case conn: ConnectionRef =>
      if (!isBlackListed(conn.nodeId.id) &&
          !isBlackListed(conn.nodeId.address)) {
        connections += conn
        messageEventBus.publish(Connection(conn.nodeId))
      } else {
        conn.handlerRef ! Close
      }

    case t @ Terminated(ref) =>
      //log.info(s"Connection dead - removing $ref ")
      connections = connections.foldLeft(Set[ConnectionRef]()) { (acc, c) =>
        if (c.handlerRef == ref) {
          executeReconnectionStrategy(c.nodeId)
          messageEventBus.publish(ConnectionLost(c.nodeId))
          acc
        } else acc + c
      }

    case c @ Connected(remote, local) =>
      log.debug(s"Got a connection to handle from $c")
      if (!isBlackListed(Option(remote.getAddress))) {

        val connection = sender()
        val handler =
          connectionHandler(connection, remote)
        context watch handler
        connection ! Register(handler,
                              keepOpenOnPeerClosed = false,
                              useResumeWriting = true)

        handler ! Begin(initialStep(remote),
                        netInf.handshakeTimeoutMs)

      }

    case ShutdownNetwork =>
      log.info("Going to shutdown all connections & unbind port")
      bindConnectionActor ! Unbind

    case cf @ CommandFailed(c: Connect) =>
      messageEventBus.publish(ConnectionFailed(c.remoteAddress, cf.cause))

      if (!isBlackListed(Option(c.remoteAddress.getAddress))) {
        strategies
          .find(_._1.isSameNotNullAddress(c.remoteAddress))
          .foreach { strategyMapElement =>
            executeReconnectionStrategy(strategyMapElement._1)
          }
      }

    case CommandFailed(cmd: Tcp.Command) =>
      log.warning(s"Failed to execute command : {}", cmd)

  }

  private def disconnect(nId: NodeId): Unit = {
    strategies = strategies.filterNot {
      case (k,v) => k.id == nId.id ||
        k.isSameNotNullAddress(nId)
    }
    connections.filter (_.nodeId.id == nId.id) foreach (_.handlerRef ! Close)

  }

  private def isConnected(nId: NodeId): Boolean =
    connections.exists(_.nodeId.id == nId.id)


  private def isBlackListed(id: String): Boolean = {

    blackListByIdentity =
      blackListByIdentity.filter(_._2 > System.currentTimeMillis())

    val result = blackListByIdentity.get(id).isDefined

    if (result) {
      log.warning("Attempting to connect to blacklisted id {}", id)
    }

    result
  }

  private def isBlackListed(remoteIpOpt: Option[InetAddress]): Boolean = {

    remoteIpOpt match {
      case Some(remoteIp) =>
        blackList = blackList.filter(_._2 > System.currentTimeMillis())

        val result = blackList.exists(_._1 == remoteIp.getAddress)
        if (result) {
          log.warning("Attempting to connect to blacklisted ip address {}",
            remoteIp)
        }
        result

      case None => false
    }
  }

  private def executeReconnectionStrategy(nodeId: NodeId) = {
    // I had used 'partition' here to avoid 2 'loops', it smelled premature.

    strategies.find(_._1.id == nodeId.id) foreach {
      case (node, strategy) =>
        val delayTime = strategy.head
        val timeToRetry = delayTime
        log.info("Reconnect strategy for {} in {} s", nodeId, delayTime)
        system.scheduler.scheduleOnce(timeToRetry seconds,
                                      self,
                                      ConnectTo(nodeId, strategy.tail))

        strategies = strategies filterNot (_._1.id == nodeId.id)
    }
  }

  def connectionHandler(
      connection: ActorRef,
      remoteAddress: InetSocketAddress
  ): ActorRef = {
    context.actorOf(
      Props(classOf[ConnectionHandlerActor],
            connection,
            remoteAddress,
            messageEventBus))
  }

  override def postStop: Unit = {
    log.warning("Network controller actor has stopped.")
    super.postStop
  }

  override def receive: Receive = waitForBind

}
