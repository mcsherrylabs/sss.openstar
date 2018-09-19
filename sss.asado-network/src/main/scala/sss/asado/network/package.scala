package sss.asado

import java.net.{InetAddress, InetSocketAddress}

import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.ConnectionHandler.HandshakeStep
import sss.asado.util.Serialize.ToBytes

package object network {

  type NetSendTo = (SerializedMessage, UniqueNodeIdentifier) => Unit
  type NetSendToMany = (SerializedMessage, Set[UniqueNodeIdentifier]) => Unit

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


  final case class ConnectionLost(nodeId: UniqueNodeIdentifier) extends AsadoEvent

  final case class Connection(nodeId: UniqueNodeIdentifier) extends AsadoEvent

  final case class ConnectionHandshakeTimeout(remote: InetSocketAddress)
      extends AsadoEvent

  final case class ConnectionFailed(remote: InetSocketAddress,
                                    cause: Option[Throwable])
      extends AsadoEvent

  final case class IncomingSerializedMessage(
      fromNodeId: UniqueNodeIdentifier,
      msg: SerializedMessage
  )

  object SerializedMessage {

    implicit val noChain: GlobalChainIdMask = 0.toByte

    def apply(msgCode: Byte)(implicit chainId: GlobalChainIdMask): SerializedMessage =
      SerializedMessage(chainId, msgCode, Array())

    def apply[T <% ToBytes](msgCode: Byte, obj: T)(implicit chainId: GlobalChainIdMask): SerializedMessage =
      SerializedMessage(chainId, msgCode, obj.toBytes)
  }

  final case class SerializedMessage private [network] (
                                                        chainId: GlobalChainIdMask,
                                                        msgCode: Byte,
                                                        data: Array[Byte])

  private val peerPattern = """(.*):(.*):(\d\d\d\d)""".r

  def toNodeId(pattern: String): NodeId = pattern match {
    case peerPattern(id, ip, port) =>
      NodeId(id, new InetSocketAddress(ip, port.toInt))
  }

  def toNodeIds(patterns: Set[String]): Set[NodeId] = patterns map toNodeId

  def indefiniteReconnectionStrategy(delaysInSeconds: Int): ReconnectionStrategy =
    Stream.continually(delaysInSeconds)

  def reconnectionStrategy(delaysInSeconds: Int*): ReconnectionStrategy =
    delaysInSeconds.toStream

}
