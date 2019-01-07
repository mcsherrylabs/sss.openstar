package sss.openstar

import java.net.{InetAddress, InetSocketAddress}

import akka.util.ByteString
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.network.ConnectionHandler.HandshakeStep
import sss.openstar.util.Serialize.ToBytes

package object network {

  type ReconnectionStrategy = Stream[Int]

  val NoReconnectionStrategy: ReconnectionStrategy = Stream.Empty

  final case class NodeId(id: String, inetSocketAddress: InetSocketAddress) {

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

    lazy val hash: ByteString = ByteString(inetSocketAddress.getAddress.getAddress) ++
      ByteString(inetSocketAddress.getPort) ++
      ByteString(id.hashCode)
  }

  type InitialHandshakeStepGenerator =
    InetSocketAddress => HandshakeStep


  final case class ConnectionLost(nodeId: UniqueNodeIdentifier) extends OpenstarEvent

  final case class Connection(nodeId: UniqueNodeIdentifier) extends OpenstarEvent

  final case class ConnectionHandshakeTimeout(remote: InetSocketAddress)
      extends OpenstarEvent

  final case class ConnectionFailed(remote: InetSocketAddress,
                                    cause: Option[Throwable])
      extends OpenstarEvent

  final case class IncomingSerializedMessage(
      fromNodeId: UniqueNodeIdentifier,
      msg: SerializedMessage
  )

  object SerializedMessage {

    implicit val noChain: GlobalChainIdMask = 0.toByte

    def apply(msgCode: Byte)(implicit chainId: GlobalChainIdMask): SerializedMessage =
      SerializedMessage(chainId, msgCode, Array())

    def apply[T](msgCode: Byte, obj: T)(implicit ev: T => ToBytes, chainId: GlobalChainIdMask): SerializedMessage =
      SerializedMessage(chainId, msgCode, obj.toBytes)
  }

  final case class SerializedMessage private [network] (
                                                        chainId: GlobalChainIdMask,
                                                        msgCode: Byte,
                                                        data: Array[Byte])


  def indefiniteReconnectionStrategy(delaysInSeconds: Int): ReconnectionStrategy =
    Stream.continually(delaysInSeconds)

  def reconnectionStrategy(delaysInSeconds: Int*): ReconnectionStrategy =
    delaysInSeconds.toStream

}
