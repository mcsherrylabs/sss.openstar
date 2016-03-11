package sss.asado.network

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import akka.actor.{Actor, ActorRef, SupervisorStrategy}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.{ByteString, CompactByteString}
import com.google.common.primitives.{Bytes, Ints}
import sss.asado.hash.FastCryptographicHash._
import sss.ancillary.Logging

import scala.util.{Failure, Success, Try}


case class NewConnection(addr:InetSocketAddress)
case class LostConnection(addr:InetSocketAddress)
case class ConnectedPeer(address: InetSocketAddress, handlerRef: ActorRef) {

  import shapeless.Typeable._
  override def equals(obj: scala.Any): Boolean = obj.cast[ConnectedPeer].exists(_.address == this.address)
}


//todo: timeout on Ack waiting
class ConnectionHandler(
                                 connection: ActorRef,
                                 remote: InetSocketAddress,
                                 ownNonce: Long) extends Actor with Buffering with Logging with Protocol {

  import PeerConnectionHandler._

  val selfPeer = new ConnectedPeer(remote, self)

  context watch connection

  override def preStart: Unit = connection ! ResumeReading

  // there is not recovery for broken connections
  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private def processErrors: Receive = {
    case CommandFailed(w: Write) =>
      log.warn(s"Write failed :$w $remote")
      //todo: blacklisting
      //peerManager.blacklistPeer(remote)
      //connection ! Close

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


  private def handshake(sentHandShake: Boolean, gotHandShake: Boolean): Receive = {

    case h: Handshake =>
      connection ! Write(ByteString(h.bytes))
      log.info(s"Handshake sent to $remote")
      if(gotHandShake) {
        context.parent ! ConnectedPeer(remote, self)
        context become(working)
      } else context become(handshake(true, false))

    case Received(data) =>
      Handshake.parse(data.toArray) match {
        case Success(shake) =>
          if (shake.fromNonce != ownNonce) {
            val delay = (System.currentTimeMillis() / 1000) - shake.time
            log.info(s"Got a Handshake from $remote, delay in s is $delay")
            connection ! ResumeReading

            if(sentHandShake) {
              context.parent ! ConnectedPeer(remote, self)
              context become(working)
            } else context.become(handshake(false, true))

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

  override def receive: Receive = handshake(false, false)
}

object PeerConnectionHandler {

  private object CommunicationState extends Enumeration {
    type CommunicationState = Value

    val AwaitingHandshake = Value("AwaitingHandshake")
    val WorkingCycle = Value("WorkingCycle")
  }

  case object CloseConnection
  case object Blacklist
}


case class NetworkMessage(msgCode: Byte, data: Array[Byte])

trait Protocol {

  def toWire(networkMessage: NetworkMessage):ByteString = {
    toWire(networkMessage.msgCode, networkMessage.data)
  }

  def toWire(msgCode: Byte, dataBytes:Array[Byte]):ByteString = {

    val dataLength: Int = dataBytes.length
    val dataWithChecksum = if (dataLength > 0) {
        val checksum = hash(dataBytes).take(Message.ChecksumLength)
        Bytes.concat(checksum, dataBytes)
    } else dataBytes //empty array

    val bytes = Message.MAGIC ++ Array(msgCode) ++ Ints.toByteArray(dataLength) ++ dataWithChecksum
    ByteString(Ints.toByteArray(bytes.length) ++ bytes)
  }

  def fromWire(bytes: ByteBuffer): Try[NetworkMessage] = Try {
    val magic = new Array[Byte](Message.MagicLength)
    bytes.get(magic)

    assert(magic.sameElements(Message.MAGIC), "Wrong magic bytes" + magic.mkString)

    val msgCode = bytes.get

    val length = bytes.getInt
    assert(length >= 0, "Data length is negative!")

    val data = new Array[Byte](length)
    //READ CHECKSUM
    val checksum = new Array[Byte](Message.ChecksumLength)
    bytes.get(checksum)

    //READ DATA
    bytes.get(data)

    //VALIDATE CHECKSUM
    val digest = hash(data).take(Message.ChecksumLength)

    //CHECK IF CHECKSUM MATCHES
    assert(checksum.sameElements(digest), s"Invalid data checksum length = $length")

    NetworkMessage(msgCode, data)
  }
}