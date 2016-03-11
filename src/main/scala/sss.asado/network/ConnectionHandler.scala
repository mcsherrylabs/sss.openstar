package sss.asado.network

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, SupervisorStrategy}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.{ByteString, CompactByteString}
import sss.ancillary.Logging

import scala.util.{Failure, Success}

case class ConnectedPeer(address: InetSocketAddress, handlerRef: ActorRef)
case class NetworkMessage(msgCode: Byte, data: Array[Byte])
case object CloseConnection

//todo: timeout on Ack waiting
class ConnectionHandler(
                                 connection: ActorRef,
                                 remote: InetSocketAddress,
                                 ownNonce: Long) extends Actor with Buffering with Logging with Protocol {

  val selfPeer = new ConnectedPeer(remote, self)

  context watch connection

  override def preStart: Unit = connection ! ResumeReading

  // there is not recovery for broken connections
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private def processErrors: Receive = {
    case CommandFailed(w: Write) =>
      log.warn(s"Write failed :$w $remote")
      connection ! ResumeReading

    case cc: ConnectionClosed =>
      log.info(s"Connection closed to : $remote ${cc.getErrorCause}")
      context.stop(self)

    case CloseConnection =>
      log.info(s"Enforced to abort communication with: $remote")
      connection ! Close

    case CommandFailed(cmd: Tcp.Command) =>
      log.info(s"Failed to execute command : $cmd ")
      connection ! ResumeReading
  }


  private val sentHandShake, gotHandShake = true
  private val handShakeNotSent, noHandShakeReceived = false

  private def handshake(sentHandShake: Boolean, gotHandShake: Boolean): Receive = {

    case h: Handshake =>
      connection ! Write(ByteString(h.bytes))
      log.info(s"Handshake sent to $remote")
      if(gotHandShake) {
        context.parent ! ConnectedPeer(remote, self)
        context become working
      } else context become handshake(sentHandShake, noHandShakeReceived)

    case Received(data) =>
      Handshake.parse(data.toArray) match {
        case Success(shake) =>
          if (shake.fromNonce != ownNonce) {
            val delay = (System.currentTimeMillis() / 1000) - shake.time
            log.info(s"Got a Handshake from $remote, delay in s is $delay")
            connection ! ResumeReading

            if(sentHandShake) {
              context.parent ! ConnectedPeer(remote, self)
              context become working
            } else context.become(handshake(handShakeNotSent, gotHandShake))

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
            log.info(s"received message from $remote")
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
        log.warn(s"Strange input for ConnectionHandler: $nonsense")
    }: Receive)

  override def receive: Receive = handshake(handShakeNotSent, noHandShakeReceived)
}

