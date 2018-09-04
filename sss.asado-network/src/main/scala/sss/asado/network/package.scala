package sss.asado

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.ActorRef
import sss.asado.network.ConnectionHandler.HandshakeStep

package object network {

  type ReconnectionStrategy = Stream[Int]

  val NoReconnectionStrategy: ReconnectionStrategy = Stream.Empty

  final case class NodeId(id: String, private[network] val inetSocketAddress: InetSocketAddress) {

    assert(Option(inetSocketAddress.getAddress).isDefined, "Cannot provide an InetSocketAddress without an IP address")
    assert(inetSocketAddress.getPort > 0, "Cannot provide an InetSocketAddress without a port")

    def isSameId(nodeId: NodeId) = id == nodeId.id

    def isSameAddress(inetSocketAddress: InetSocketAddress) =
      inetSocketAddress.getAddress == address


    def isSameAddress(nId: NodeId) =
      nId.address == address

    def isSameAddress(inetAddress: InetAddress) =
      address == inetAddress

    val address = inetSocketAddress.getAddress

    override def toString: String = {
      s"NodeId id:$id, address: $address (!=port:${inetSocketAddress.getPort})"
    }

  }

  type InitialHandshakeStepGenerator =
    InetSocketAddress => HandshakeStep

  type UniqueNodeIdentifier = String

  final case class ConnectionLost(nodeId: UniqueNodeIdentifier) extends AsadoEvent

  final case class Connection(nodeId: UniqueNodeIdentifier) extends AsadoEvent

  final case class ConnectionHandshakeTimeout(remote: InetSocketAddress)
      extends AsadoEvent

  final case class ConnectionFailed(remote: InetSocketAddress,
                                    cause: Option[Throwable])
      extends AsadoEvent

  final case class IncomingNetworkMessage(
      fromNodeId: UniqueNodeIdentifier,
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
