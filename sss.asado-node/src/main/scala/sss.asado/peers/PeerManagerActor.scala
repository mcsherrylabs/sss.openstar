package sss.asado.peers

import akka.actor.Actor
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
import sss.asado.chains.Chains.GlobalChainIdMask

import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.{MessageEventBus, _}
import sss.asado.peers.PeerManager.{Capabilities, ChainQuery, IdQuery, PeerConnection, Query, UnQuery}



private class PeerManagerActor( ncRef: NetworkRef,
                                bootstrapNodes: Set[NodeId],
                                ourCapabilities: Capabilities,
                                messageEventBus: MessageEventBus,

                      ) extends Actor {

  private case class KnownConnection(c: Connection, cabs: Capabilities)

  import SerializedMessage.noChain

  private val ourCapabilitiesNetworkMsg: SerializedMessage =
    SerializedMessage(MessageKeys.Capabilities, ourCapabilities)

  private var queries: Set[Query] = Set()

  private var knownConns : Map[UniqueNodeIdentifier, Capabilities] = Map()

  messageEventBus.subscribe(MessageKeys.Capabilities)
  messageEventBus.subscribe(MessageKeys.QueryCapabilities)
  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[Connection])
  messageEventBus.subscribe(classOf[ConnectionFailed])
  messageEventBus.subscribe(classOf[ConnectionHandshakeTimeout])

  bootstrapNodes foreach (ncRef.connect(_, indefiniteReconnectionStrategy(1)))


  private def matchWithCapabilities(nodeId: UniqueNodeIdentifier,
                                    otherNodesCaps: Capabilities)(q:Query): Boolean = {
    q match {
      case ChainQuery(chainId: GlobalChainIdMask) =>
        otherNodesCaps.contains(chainId)

      case IdQuery(ids: Set[String]) =>
        ids contains nodeId

      case _ => false
    }
  }

  override def receive: Receive = {

    case UnQuery(q) => queries -= q

    case q@IdQuery(ids) =>
      queries += q
      //ids foreach (ncRef.connect(_, indefiniteReconnectionStrategy(30)))

    case q: Query => queries += q


    case Connection(nodeId) =>
      ncRef.send(SerializedMessage(0.toByte, MessageKeys.QueryCapabilities, Array()), Set(nodeId))

    case ConnectionLost(lost) =>
      knownConns -= lost

    case IncomingMessage(_, MessageKeys.QueryCapabilities, nodeId, _) =>
      // TODO if spamming, blacklist
      ncRef.send(ourCapabilitiesNetworkMsg, Set(nodeId))

    case IncomingMessage(_, MessageKeys.Capabilities, nodeId, otherNodesCaps: Capabilities) =>
      knownConns += nodeId -> otherNodesCaps

      if(queries.exists (matchWithCapabilities(nodeId, otherNodesCaps))) {
        messageEventBus.publish(PeerConnection(nodeId, otherNodesCaps))
      }
  }
}
