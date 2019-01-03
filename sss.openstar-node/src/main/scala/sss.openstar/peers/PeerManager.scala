package sss.openstar.peers

import akka.actor.{ActorSystem, Props}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.{OpenstarEvent, UniqueNodeIdentifier}
import sss.openstar.network.{MessageEventBus, _}
import sss.openstar.peers.Discovery.DiscoveredNode
import sss.openstar.peers.PeerManager.{AddQuery, Query, UnQuery}


import scala.concurrent.duration.FiniteDuration


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

class PeerManager(connect: NetConnect,
                  send: NetSend,
                  bootstrapNodes: Set[DiscoveredNode],
                  ourCapabilities: Capabilities,
                  discoveryInterval: FiniteDuration,
                  discovery: Discovery
                  )
                 (implicit actorSystem: ActorSystem,
                  events: MessageEventBus
                 ) extends PeerQuery {

  bootstrapNodes foreach { dn => discovery.insert(dn.nodeId, dn.capabilities) }

  override def addQuery(q: Query): Unit = {
    ref ! AddQuery(q)
  }

  override def removeQuery(q: Query): Unit = ref ! UnQuery(q)

  private val ref = actorSystem.actorOf(Props(classOf[PeerManagerActor],
    connect,
    send,
    ourCapabilities,
    discoveryInterval,
    discovery,
    events
    ), "PeerManagerActor")

}

