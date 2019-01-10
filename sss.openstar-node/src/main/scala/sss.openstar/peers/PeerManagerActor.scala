package sss.openstar.peers

import java.net.InetSocketAddress

import akka.actor.{Actor, Cancellable}
import sss.openstar.{MessageKeys, UniqueNodeIdentifier}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.network.MessageEventBus.IncomingMessage
import sss.openstar.network.{MessageEventBus, _}
import sss.openstar.peers.Discovery.DiscoveredNode
import sss.openstar.peers.DiscoveryActor.Discover
import sss.openstar.peers.PeerManager.{AddQuery, ChainQuery, IdQuery, PeerConnection, Query, UnQuery}

import scala.concurrent.duration.{Duration, FiniteDuration}

private class PeerManagerActor(connect: NetConnect,
                               send: NetSend,
                               ourCapabilities: Capabilities,
                               discoveryInterval: FiniteDuration,
                               discovery: Discovery,
                               thisNodesId: UniqueNodeIdentifier
                              )
                              (implicit events: MessageEventBus)
  extends Actor {

  private case class KnownConnection(c: Connection, cabs: Capabilities)
  private case object WakeUp

  import SerializedMessage.noChain

  private val ourCapabilitiesNetworkMsg: SerializedMessage =
    SerializedMessage(MessageKeys.Capabilities, ourCapabilities)

  private var wakeTimer: Option[Cancellable] = None

  private val skippedWakeUpIntervalsPlusOne = 3
  private var resetFailCount: Int = skippedWakeUpIntervalsPlusOne
  private var queries: Set[Query] = Set()

  private var knownConns : Map[UniqueNodeIdentifier, Capabilities] = Map()
  private var failedConns: Seq[InetSocketAddress] = Seq.empty
  private var reverseNodeInetAddrLookup: Seq[NodeId] = Seq.empty

  events.subscribe(MessageKeys.Capabilities)
  events.subscribe(MessageKeys.QueryCapabilities)
  events.subscribe(classOf[ConnectionLost])
  events.subscribe(classOf[Connection])
  events.subscribe(classOf[ConnectionFailed])
  events.subscribe(classOf[ConnectionHandshakeTimeout])

  private def minusThisNode(nodes:Seq[DiscoveredNode]): Seq[DiscoveredNode] = {
    nodes filterNot (_.nodeId.id == thisNodesId)
  }

  private def matchWithCapabilities(nodeId: UniqueNodeIdentifier,
                                    otherNodesCaps: Capabilities)(q:Query): Boolean = {
    q match {
      case ChainQuery(chainId: GlobalChainIdMask, _) =>
        otherNodesCaps.contains(chainId)

      case IdQuery(ids: Set[String]) =>
        ids contains nodeId

      case _ => false
    }
  }

  override def receive: Receive = {

    case WakeUp =>

      resetFailCount -= 1
      if(resetFailCount == 0) {
        //Recycle failed conns to give them a chance to come up.
        failedConns = Seq.empty
        resetFailCount = skippedWakeUpIntervalsPlusOne
      }

      queries foreach (self ! _)
      import context.dispatcher
      wakeTimer map (_.cancel())
      wakeTimer = Option(context.system.scheduler.scheduleOnce(discoveryInterval, self, WakeUp))
      events publish Discover(knownConns.keys.toSet)

    case UnQuery(q) => queries -= q

    case AddQuery(q) =>
      queries += q
      self ! WakeUp

    case q@IdQuery(ids) =>
      val notAlreadyConnected = ids diff knownConns.keySet
      val nodeIds = discovery.lookup(notAlreadyConnected)
      minusThisNode(nodeIds) foreach (n => connect(n.nodeId, indefiniteReconnectionStrategy(30)))

    case q@ChainQuery(cId, requestedConns) =>
      val goodConns = knownConns.filter {
        case (nId, caps) => matchWithCapabilities(nId, caps)(q)
      }.keySet

      if(goodConns.size < requestedConns) {
        val allIgnoredConns = goodConns ++
          reverseNodeInetAddrLookup.filterNot(ni => failedConns.contains(ni.address)).map(_.id)

        val newNodes = discovery.find(requestedConns, cId, allIgnoredConns)
        minusThisNode(newNodes) foreach { n =>
          connect(n.nodeId, indefiniteReconnectionStrategy(1))
          reverseNodeInetAddrLookup = n.nodeId +: reverseNodeInetAddrLookup
        }
      }


    case Connection(nodeId) =>
      failedConns = reverseNodeInetAddrLookup.find(_.id == nodeId)
      .map (found => failedConns.filterNot(_ == found.inetSocketAddress))
          .getOrElse(failedConns)

      send(SerializedMessage(0.toByte, MessageKeys.QueryCapabilities, Array.emptyByteArray), Set(nodeId))

    case ConnectionLost(lost) =>
      knownConns -= lost

    case ConnectionFailed(remote, _) =>
      failedConns = remote +: failedConns
      discovery.unreachable(remote)

    case IncomingMessage(_, MessageKeys.QueryCapabilities, nodeId, _) =>
      // TODO if spamming, blacklist
      send(ourCapabilitiesNetworkMsg, Set(nodeId))

    case IncomingMessage(_, MessageKeys.Capabilities, nodeId, otherNodesCaps: Capabilities) =>

      knownConns += nodeId -> otherNodesCaps

      reverseNodeInetAddrLookup.find(_.id == nodeId) foreach { found =>
        discovery.reachable(found.inetSocketAddress)
      }

      if(queries.exists (matchWithCapabilities(nodeId, otherNodesCaps))) {
        events.publish(PeerConnection(nodeId, otherNodesCaps))
      }
  }
}
