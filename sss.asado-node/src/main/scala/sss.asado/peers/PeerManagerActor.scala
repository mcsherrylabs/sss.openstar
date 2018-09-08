package sss.asado.peers

import akka.actor.Actor
import sss.asado.chains.GlobalChainIdMask
import sss.asado.network._
import sss.asado.peers.PeerManager.{Capabilities, ChainQuery, IdQuery, PeerConnection, Query, UnQuery}
import sss.asado.util.IntBitSet


class PeerManagerActor( ncRef: NetworkRef,
                        ourCapabilities: Capabilities,
                        messageEventBus: MessageEventBus
                      ) extends Actor {

  private case class KnownConnection(c: Connection, cabs: Capabilities)

  var queries: Set[Query] = Set()

  var knownConns : Map[UniqueNodeIdentifier, GlobalChainIdMask] = Map()

  messageEventBus.subscribe(classOf[Capabilities])
  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[Connection])

  private def matchWithCapabilities(caps: Capabilities)(q:Query): Boolean = {
    q match {
      case ChainQuery(chainId: GlobalChainIdMask) =>
        IntBitSet(chainId).intersects(caps.supportedChains)

      case IdQuery(ids: Set[String]) =>
        ids contains caps.nodeId

      case _ => false
    }
  }
  override def receive: Receive = {

    case UnQuery(q) => queries -= q

    case q: Query => queries += q

    case connection: Connection =>
      val nm = ourCapabilities.toNetworkMessage
      ncRef.send(nm, connection.nodeId)

    case ConnectionLost(lost) =>
      knownConns -= lost

    case c@Capabilities(mask, nodeId) =>
      knownConns += nodeId -> mask

      queries
        .find (matchWithCapabilities(c))
        .foreach (_ => messageEventBus.publish(PeerConnection(c)))

  }
}
