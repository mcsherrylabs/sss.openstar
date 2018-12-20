package sss.openstar

import java.net.InetSocketAddress

import akka.util.ByteString
import sss.openstar.chains.Chains.GlobalChainIdMask


import sss.openstar.peers.serialize.{PeerPageResponseSerializer, PeerPageSerializer}
import sss.openstar.util.IntBitSet
import sss.openstar.util.Serialize._

package object peers {

  case class PeerPage(start: Int, end: Int, fingerprint: ByteString)
  case class PeerPageResponse(nodeId: String, sockAddr:InetSocketAddress, capabilities: Capabilities)

  case class Capabilities(supportedChains: GlobalChainIdMask) {
    def contains(chainIdMask: GlobalChainIdMask): Boolean = {
      IntBitSet(supportedChains).contains(chainIdMask)
    }
  }

  implicit class CapabilitiesToBytes(val c: Capabilities) extends ToBytes {
    def toBytes: Array[Byte] = ByteSerializer(c.supportedChains).toBytes
  }

  implicit class CapabilitiesFromBytes(val bs: Array[Byte]) extends AnyVal {
    def toCapabilities: Capabilities = Capabilities(bs.extract(ByteDeSerialize))
  }

  implicit class PeerPageToBytes(val p:PeerPage) extends ToBytes {
    override def toBytes: Array[Byte] = PeerPageSerializer.toBytes(p)
  }

  implicit class PeerPageFromBytes(val bs: Array[Byte])  {
    def toPeerPage: PeerPage = PeerPageSerializer.fromBytes(bs)
  }

  implicit class PeerPageResponseToBytes(val p:PeerPageResponse) extends ToBytes {
    override def toBytes: Array[Byte] = PeerPageResponseSerializer.toBytes(p)
  }

  implicit class PeerPageResponseFromBytes(val bs: Array[Byte])  {
    def toPeerPageResponse: PeerPageResponse = PeerPageResponseSerializer.fromBytes(bs)
  }
}
