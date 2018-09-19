package sss.asado.peers

import akka.actor.{ActorSystem, Props}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.{AsadoEvent, UniqueNodeIdentifier}
import sss.asado.network.{MessageEventBus, _}
import sss.asado.nodebuilder.Encoder
import sss.asado.peers.PeerManager.{Capabilities, Query, UnQuery}
import sss.asado.util.IntBitSet
import sss.asado.util.Serialize._


trait PeerQuery {
  def addQuery(q:Query): Unit
  def removeQuery(q:Query): Unit
}

object PeerManager {

  case class PeerConnection(nodeId: UniqueNodeIdentifier, c: Capabilities) extends AsadoEvent
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
                  eventMessageBus: MessageEventBus,
                  encode: Encoder)
                 (implicit actorSystem: ActorSystem) extends PeerQuery {


  override def addQuery(q: Query): Unit = {
    ref ! q
  }

  override def removeQuery(q: Query): Unit = ref ! UnQuery(q)

  private val ref = actorSystem.actorOf(Props(classOf[PeerManagerActor],
    networkRef,
    bootstrapNodes,
    ourCapabilities,
    eventMessageBus,
    encode))

  // register for connections
  // on connection get the supported chains
  // add to database
  // check against the filters if match publish PeerQueryMatch(id:Identity)
  // get connection
  // if the connection fails try another one.
  // incoming connections get added also.

}

