package sss.asado.network

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, _}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import sss.asado.UniqueNodeIdentifier
import sss.asado.network.ConnectionHandler.{Begin, ConnectionRef, HandshakeStep}
import sss.asado.network.NetworkControllerActor._

import scala.concurrent.duration._
import scala.concurrent.Promise
import scala.util.{Failure, Random}
import language.postfixOps

private[network] object NetworkControllerActor {

  case class SendToNodeId(msg: SerializedMessage, nId: UniqueNodeIdentifier)
  case class ConnectTo(nodeId: NodeId,
                       reconnectionStrategy: ReconnectionStrategy)

  case class UnBlackListAddr(inetAddress: InetAddress)
  case class UnBlackList(id: UniqueNodeIdentifier)
  case class Disconnect(nodeId: UniqueNodeIdentifier)
  case class BlackListAddr(inetAddress: InetAddress, duration: Duration)
  case class BlackList(id: UniqueNodeIdentifier, duration: Duration)
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

  //private val connectionsRef: AtomicReference[Set[Connection]]= new AtomicReference[Set[Connection]](Set())

  private val stopPromise: Promise[Unit] = Promise()
  private var connections = Set[ConnectionRef]()
  private var strategies = Map[UniqueNodeIdentifier, (InetSocketAddress, ReconnectionStrategy)]()

  private var blackList = Map[InetAddress, Long]()
  private var blackListByIdentity = Map[UniqueNodeIdentifier, Long]()

  /*context.actorOf(
    Props(
      classOf[ConnectionTracker],
      connectionsRef,
      messageEventBus
    )
  )*/

  IO(Tcp) ! Bind(self, netInf.localAddress)

  private def waitForBind: Actor.Receive = {
    case b @ Bound(localAddr) =>
      log.info("Successfully bound to " + localAddr)
      context.become(manageNetwork(sender()))
      startPromise.success(new NetworkRef(
        self,
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

    case SendToNodeId(nm, nodeId) =>
      // TODO this *could* be a promise that is completed
      // TODO with failure if the nodeid is unknown or no such ActorRef.
      connections
        .find(_.nodeId.id == nodeId)
        .foreach(_.handlerRef ! nm)

    case c @ ConnectTo(n @ NodeId(nodeId, addr), reconnnectStrategy) =>
      log.info(s"ConnectTo $nodeId @ $addr")
      if (!isConnected(n) &&
          !isBlackListed(n.address) &&
          !isBlackListed(nodeId)) {

        if (!reconnnectStrategy.isEmpty) {
          strategies += nodeId -> (addr, reconnnectStrategy)
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
        messageEventBus.publish(Connection(conn.nodeId.id))
      } else {
        conn.handlerRef ! Close
      }

    case t @ Terminated(ref) =>
      //log.info(s"Connection dead - removing $ref ")
      connections = connections.foldLeft(Set[ConnectionRef]()) { (acc, c) =>
        if (c.handlerRef == ref) {
          executeReconnectionStrategy(c.nodeId.id)
          messageEventBus.publish(ConnectionLost(c.nodeId.id))
          acc
        } else acc + c
      }

    case c @ Connected(remote, local) =>
      log.debug(s"Got a connection to handle from $c")
      if (!isBlackListed(remote.getAddress)) {

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

      if (!isBlackListed(c.remoteAddress.getAddress)) {
        strategies
          .find { case (id, (addr, strategy)) =>
            addr == c.remoteAddress
          }
          .foreach { case (id, (addr, strategy)) =>
            executeReconnectionStrategy(id)
          }
      }

    case CommandFailed(cmd: Tcp.Command) =>
      log.warning(s"Failed to execute command : {}", cmd)

  }

  private def disconnect(id: UniqueNodeIdentifier): Unit = {
    strategies -= id
    connections.filter (_.nodeId.id == id) foreach (_.handlerRef ! Close)
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

  private def isBlackListed(remoteIp: InetAddress): Boolean = {

      blackList = blackList.filter(_._2 > System.currentTimeMillis())

      val result = blackList.exists(_._1 == remoteIp.getAddress)
      if (result) {
        log.warning("Attempting to connect to blacklisted ip address {}",
          remoteIp)
      }
      result

  }

  private def executeReconnectionStrategy(nodeId: UniqueNodeIdentifier) = {
    // I had used 'partition' here to avoid 2 'loops', it smelled premature.

    strategies.get(nodeId) foreach { case (addr, strategy) =>
        val delayTime = strategy.head
        val timeToRetry = delayTime
        log.debug("Reconnect strategy for {} in {} s", nodeId, delayTime)
        system.scheduler.scheduleOnce(timeToRetry seconds,
                                      self,
                                      ConnectTo(NodeId(nodeId, addr), strategy.tail))

        strategies -= nodeId
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
            messageEventBus).withDispatcher("blocking-dispatcher"))
  }

  override def postStop: Unit = {
    log.warning("Network controller actor has stopped.")
    super.postStop
  }

  override def receive: Receive = waitForBind

}
