package sss.openstar.peers

import akka.actor.{ActorSystem, Props}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.{OpenstarEvent, UniqueNodeIdentifier}
import sss.openstar.network.{MessageEventBus, _}
import sss.openstar.peers.PeerManager.{Capabilities, Query, UnQuery}
import sss.openstar.util.IntBitSet
import sss.openstar.util.Serialize._


trait PeerQuery {
  def addQuery(q:Query): Unit
  def removeQuery(q:Query): Unit
}

object PeerManager {

  case class PeerConnection(nodeId: UniqueNodeIdentifier, c: Capabilities) extends OpenstarEvent
  case class UnQuery(q: Query)

  trait Query
  case class ChainQuery(chainId: GlobalChainIdMask) extends Query
  case class IdQuery(ids: Set[UniqueNodeIdentifier]) extends Query

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

}

class PeerManager(networkRef: NetworkRef,
                  bootstrapNodes: Set[NodeId],
                  ourCapabilities: Capabilities,
                  eventMessageBus: MessageEventBus
                  )
                 (implicit actorSystem: ActorSystem) extends PeerQuery {


  override def addQuery(q: Query): Unit = {
    ref ! q
  }

  override def removeQuery(q: Query): Unit = ref ! UnQuery(q)

  private val ref = actorSystem.actorOf(Props(classOf[PeerManagerActor],
    networkRef,
    bootstrapNodes,
    ourCapabilities,
    eventMessageBus
    ), "PeerManagerActor")

}

