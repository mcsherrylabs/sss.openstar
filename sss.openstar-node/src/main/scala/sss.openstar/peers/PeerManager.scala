package sss.openstar.peers

import akka.actor.{ActorSystem, Props}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.{OpenstarEvent, UniqueNodeIdentifier}
import sss.openstar.network.{MessageEventBus, _}
import sss.openstar.peers.PeerManager.{Query, UnQuery}
import sss.openstar.util.IntBitSet
import sss.openstar.util.Serialize._


trait PeerQuery {
  def addQuery(q:Query): Unit
  def removeQuery(q:Query): Unit
}

object PeerManager {

  case class PeerConnection(nodeId: UniqueNodeIdentifier, c: Capabilities) extends OpenstarEvent
  case class UnQuery(q: Query)
  case class AddQuery(q: Query)

  trait Query
  case class ChainQuery(chainId: GlobalChainIdMask, numConns: Int) extends Query
  case class IdQuery(ids: Set[UniqueNodeIdentifier]) extends Query


}

class PeerManager(networkRef: NetworkRef,
                  bootstrapNodes: Set[(NodeId, Capabilities)],
                  ourCapabilities: Capabilities,
                  eventMessageBus: MessageEventBus,
                  discovery: Discovery
                  )
                 (implicit actorSystem: ActorSystem) extends PeerQuery {

  bootstrapNodes foreach {case (n, c) => discovery.insert(n, c.supportedChains) }

  override def addQuery(q: Query): Unit = {
    ref ! q
  }

  override def removeQuery(q: Query): Unit = ref ! UnQuery(q)

  private val ref = actorSystem.actorOf(Props(classOf[PeerManagerActor],
    networkRef,

    ourCapabilities,
    eventMessageBus
    ), "PeerManagerActor")

}

