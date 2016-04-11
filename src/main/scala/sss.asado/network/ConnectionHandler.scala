package sss.asado.network

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, SupervisorStrategy}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.{ByteString, CompactByteString}

import scala.util.{Failure, Success}

case class NodeId(id: String, inetSocketAddress: InetSocketAddress)
case class Connection(nodeId: NodeId, handlerRef: ActorRef)
case class NetworkMessage(msgCode: Byte, data: Array[Byte])

case object CloseConnection

class ConnectionHandler(
                                 connection: ActorRef,
                                 remote: InetSocketAddress,
                                 ownNonce: Long) extends Actor with Buffering with ActorLogging with Protocol {

  context watch connection

  override def preStart: Unit = connection ! ResumeReading

  // there is not recovery for broken connections
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private def processErrors: Receive = {
    case CommandFailed(w: Write) =>
      log.warning(s"Write failed :$w $remote")
      connection ! ResumeReading

    case cc: ConnectionClosed =>
      log.debug(s"Connection closed to : $remote ${cc.getErrorCause}")
      context.stop(self)

    case CloseConnection =>
      log.debug(s"Programmer enforced connection close with: $remote")
      connection ! Close

    case CommandFailed(cmd: Tcp.Command) =>
      log.warning(s"Failed to execute command : $cmd ")
      connection ! ResumeReading
  }


  private val sentHandShakeTrue = true
  private val handShakeNotSent = false

  private def handshake(sentHandShake: Boolean, remoteIdOpt: Option[String]): Receive = {

    case h: Handshake =>
      connection ! Write(ByteString(h.bytes))
      remoteIdOpt match {
        case Some(remoteId) =>
          context.parent ! Connection(NodeId(remoteId, remote), self)
          context become working
        case None => context become handshake(sentHandShakeTrue, remoteIdOpt)
      }

    case Received(data) =>
      Handshake.parse(data.toArray) match {
        case Success(shake) =>
          if (shake.fromNonce != ownNonce) {
            val delay = (System.currentTimeMillis() / 1000) - shake.time
            log.debug(s"Got a Handshake from $remote, delay in s is $delay")
            connection ! ResumeReading

            if(sentHandShake) {
              context.parent ! Connection(NodeId(shake.nodeId, remote), self)
              context become working
            } else context.become(handshake(handShakeNotSent, Some(shake.nodeId)))

          } else {
            log.info(s"Got a handshake from myself?!")
            connection ! Close
          }
        case Failure(t) =>
          log.info(s"Error during parsing a handshake: $t")
          connection ! Close
      }

  }

  private var chunksBuffer: ByteString = CompactByteString()

  def handleMessages: Receive = {
    case m@NetworkMessage(msgCode, data) =>
      val bytes = toWire(m)
      connection ! Write(bytes)

    case Received(data) =>

      val t = getPacket(chunksBuffer ++ data)
      chunksBuffer = t._2

      t._1.find { packet =>
        fromWire(packet.toByteBuffer) match {
          case Success(message) =>
            context.parent ! message
            false

          case Failure(e) =>
            log.info(s"Corrupted data from: " + remote, e)
            //connection ! Close
            //  context stop self
            true
        }
      }
      connection ! ResumeReading
  }

  def working: Receive =
    handleMessages orElse
      processErrors orElse ({
      case nonsense: Any =>
        log.warning(s"Strange input for ConnectionHandler: $nonsense")
    }: Receive)

  override def receive: Receive = handshake(handShakeNotSent, None)
}

