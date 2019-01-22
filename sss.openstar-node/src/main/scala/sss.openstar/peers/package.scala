package sss.openstar

import java.net.{InetAddress, InetSocketAddress}

import akka.util.ByteString
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.ledger.LedgerItem
import sss.openstar.peers.serialize.{InetAddressSerializer, InetSocketAddressSerializer, PeerPageResponseSerializer, PeerPageSerializer}
import sss.openstar.util.{IntBitSet, SeqSerializer}
import sss.openstar.util.Serialize._

package object peers {

  case class PeerPage(start: Int, end: Int, fingerprint: ByteString)
  case class PeerPageResponse(nodeId: String, sockAddr:InetSocketAddress, capabilities: Capabilities)

  case class SeqPeerPageResponse(val value: Seq[PeerPageResponse]) extends AnyVal

  implicit class SeqPeerPageResponseToBytes(v: SeqPeerPageResponse) extends ToBytes {
    override def toBytes: Array[Byte] = SeqSerializer.toBytes(v.value map (_.toBytes))
  }

  implicit class SeqPeerPageResponseFromBytes(bytes: Array[Byte]) {
    def toSeqPeerPageResponse: SeqPeerPageResponse =
      SeqPeerPageResponse(
        SeqSerializer.fromBytes(bytes) map (_.toPeerPageResponse)
      )
  }

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

  implicit class InetAddressToBytes(val inetAddr: InetAddress)  extends ToBytes {
    def toBytes: Array[Byte]= InetAddressSerializer.toBytes(inetAddr)
  }

  implicit class InetAddressFromBytes(val bs: Array[Byte])  {
    def toInetAddress: InetAddress = InetAddressSerializer.fromBytes(bs)
  }

  implicit class InetAddressSocketToBytes(val inetAddr: InetSocketAddress)  extends ToBytes {
    def toBytes: Array[Byte]= InetSocketAddressSerializer.toBytes(inetAddr)
  }

  implicit class InetAddressSocketFromBytes(val bs: Array[Byte])  {
    def toInetSocketAddress: InetSocketAddress = InetSocketAddressSerializer.fromBytes(bs)
  }
}
