package sss.asado.network

import java.net.InetSocketAddress

import language.postfixOps
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, SupervisorStrategy}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.{ByteString, CompactByteString}

import concurrent.duration._
import util.{Failure, Success}

object ConnectionHandler {
  case class Begin(
      initialStep: HandshakeStep,
      totalAllowedHandshakeTimeMs: Int
  )

  sealed trait HandshakeStep
  final case object RejectConnection
    extends HandshakeStep

  final case class BytesToSend(send: ByteString, nextStep: HandshakeStep)
    extends HandshakeStep

  final case class WaitForBytes(handleBytes: ByteString => HandshakeStep)
    extends HandshakeStep

  final case class ConnectionEstablished(nodeId: NodeId)
    extends HandshakeStep

  final case class ConnectionRef(nodeId: NodeId, handlerRef: ActorRef)

}

//TODO Flow control, back pressure, ACK ....
class ConnectionHandlerActor(
    connection: ActorRef,
    remote: InetSocketAddress,
    eventBus: MessageEventBus
) extends Actor
    with Buffering
    with ActorLogging
    with Protocol {

  private case class TooSlow(totalAllowedHandshakeTimeMs: Int)

  import context.dispatcher
  import ConnectionHandler._

  private var cancellable: Option[Cancellable] = None

  context watch connection

  override def preStart: Unit = connection ! ResumeReading

  override def postStop(): Unit = log.debug("Connection handler for {} down", remote)

  // there is not recovery for broken connections
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private def processErrors: Receive = {

    case CommandFailed(w: Write) =>
      log.warning(s"Write failed :$w $remote")
      connection ! ResumeWriting

    case cc: ConnectionClosed =>
      log.debug(s"Connection closed to : $remote ${Option(cc.getErrorCause)}")
      context.stop(self)

    case CommandFailed(cmd: Tcp.Command) =>
      log.warning(s"Failed to execute command : $cmd ")
      connection ! ResumeReading
      connection ! ResumeWriting

    case nonsense: Any =>
      log.warning(s"Strange input for ConnectionHandler: $nonsense")
  }

  private def process(step: HandshakeStep): Unit = {
    step match {
      case BytesToSend(send, nextStep) =>
        connection ! Write(send)
        process(nextStep)
      case n @ WaitForBytes(_) =>
        context.become(handshake(n))
        connection ! ResumeReading
      case ConnectionEstablished(nodeId) =>
        cancellable map (_.cancel())
        context become connectionEstablished(nodeId)
        connection ! ResumeReading
        context.parent ! ConnectionRef(nodeId, self)

      case RejectConnection =>
        log.debug(s"Programmer enforced connection close with: $remote")
        connection ! Close // this will cause this actor to stop via ConnectionClosed.
    }
  }

  private def closeOnTimeout: Receive = {
    case TooSlow(totalAllowedHandshakeTimeMs) =>
      log.warning(
        "Handshake not completed within {} ms, closing connection with {}",
        totalAllowedHandshakeTimeMs,
        remote)
      eventBus.publish(ConnectionHandshakeTimeout(remote))
  }

  override def receive: Receive = closeOnTimeout orElse {
    case Begin(initialConnectionHandler, totalAllowedHandshakeTimeMs: Int) =>
      cancellable = Option(
        context.system.scheduler.scheduleOnce(
          totalAllowedHandshakeTimeMs milliseconds,
          self,
          TooSlow(totalAllowedHandshakeTimeMs)))
      process(initialConnectionHandler)

    // In the case where we get bytes from the opposite
    // end before we process initial step on this end
    case r: Received => self ! r
  }

  private def handshake(w: WaitForBytes): Receive = closeOnTimeout orElse {

    case Received(data) =>
      process(w.handleBytes(data))
  }

  private var chunksBuffer: ByteString = CompactByteString()

  def sendAndReceiveMessages(nId: NodeId): Receive = {
    case m: SerializedMessage =>
      log.debug(s"Sending $m to $remote")
      val bytes = toWire(m)
      connection ! Write(bytes)

    case Received(data) =>
      val t = getPacket(chunksBuffer ++ data)
      chunksBuffer = t._2

      t._1.find { packet =>
        fromWire(packet.toByteBuffer) match {
          case Success(s) =>
            eventBus.publish(
              IncomingSerializedMessage(nId.id, s))
            false

          case Failure(e) =>
            log.warning("Corrupted data from: {} {}", remote, e)
            true
        }
      }
      connection ! ResumeReading

    case Close =>
      connection ! Close
  }

  def connectionEstablished(nId: NodeId): Receive =
    sendAndReceiveMessages(nId) orElse
      processErrors

}
