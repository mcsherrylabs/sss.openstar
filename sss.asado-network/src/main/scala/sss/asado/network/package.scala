package sss.asado

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.ActorRef
import sss.asado.network.ConnectionHandler.HandshakeStep

package object network {

  type ReconnectionStrategy = Stream[Int]

  val NoReconnectionStrategy: ReconnectionStrategy = Stream.Empty

  final case class NodeId(id: String, private[network] val inetSocketAddress: InetSocketAddress) {

    def isSameId(nodeId: NodeId) = id == nodeId.id

    def isSameNotNullAddress(inetSocketAddress: InetSocketAddress) = {
      (for {
        addr1 <- Option(inetSocketAddress.getAddress)
        addr2 <- address
      } yield (addr1 == addr2))
        .getOrElse(false)
    }

    def isSameNotNullAddress(nId: NodeId) =
      (for {
        addr1 <- nId.address
        addr2 <- address
      } yield (addr1 == addr2)).getOrElse(false)


    def isSameNotNullAddress(inetAddress: InetAddress) = address.map (_ == inetAddress).getOrElse(false)

    val address = Option(inetSocketAddress.getAddress)

    private lazy val port = Option(inetSocketAddress.getPort)

    override def toString: String = {
      s"NodeId id:$id, address: $address (!=port:$port)"
    }

    override def equals(that: scala.Any): Boolean = {
      that match {
        case that: NodeId => {
          this.id == that.id &&
            this.address == that.address
        }
        case _ => false
      }
    }

    override def hashCode: Int = {
        id.hashCode +
          Option(inetSocketAddress.getAddress)
            .map(_.hashCode())
            .getOrElse(0)
    }

  }

  type InitialHandshakeStepGenerator =
    InetSocketAddress => HandshakeStep

  final case class ConnectionLost(nodeId: NodeId) extends AsadoEvent

  final case class Connection(nodeId: NodeId) extends AsadoEvent


  final case class ConnectionHandshakeTimeout(remote: InetSocketAddress)
      extends AsadoEvent
  final case class ConnectionFailed(remote: InetSocketAddress,
                                    cause: Option[Throwable])
      extends AsadoEvent

  final case class IncomingNetworkMessage(
      //connControllerRef: ActorRef,
      fromNodeId: NodeId,
      msgCode: Byte,
      data: Array[Byte]
  )

  final case class NetworkMessage(msgCode: Byte, data: Array[Byte])

  private val peerPattern = """(.*):(.*):(\d\d\d\d)""".r

  def toNodeId(pattern: String): NodeId = pattern match {
    case peerPattern(id, ip, port) =>
      NodeId(id, new InetSocketAddress(ip, port.toInt))
  }

  def toNodeIds(patterns: Set[String]): Set[NodeId] = patterns map toNodeId

  def reconnectionStrategy(delaysInSeconds: Int*): ReconnectionStrategy =
    delaysInSeconds.toStream

}
