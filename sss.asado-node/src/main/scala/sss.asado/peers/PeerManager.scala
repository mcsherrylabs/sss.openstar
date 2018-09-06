package sss.asado.peers

import akka.actor.{Actor, ActorSystem, Props}
import sss.asado.AsadoEvent
import sss.asado.chains.GlobalChainIdMask
import sss.asado.network.MessageEventBus.HasNodeId
import sss.asado.network._
import sss.asado.peers.PeerManager.Query


object PeerManager {

  case class PeerConnection(c: Capabilities) extends AsadoEvent
  case class UnQuery(q: Query)

  trait Query
  case class ChainQuery(chainId: GlobalChainIdMask) extends Query
  case class IdQuery(ids: Set[String]) extends Query

  case class Capabilities(supportedChains: GlobalChainIdMask,
                          nodeId: UniqueNodeIdentifier)
    extends HasNodeId

  case class QueryCapabilities(supportedChains: GlobalChainIdMask,
                          nodeId: UniqueNodeIdentifier)
    extends HasNodeId

  implicit class CapabilitiesOps(val caps: Capabilities) extends AnyVal {
    def toNetworkMessage: NetworkMessage = ???
  }
}

class PeerManager(messageEventBus: MessageEventBus)
                 (implicit actorSystem: ActorSystem) {


  def addQuery(q: Query) = {
    ref ! q
  }

  case class PeerQueryMatch(id:String, chainId: GlobalChainIdMask) extends AsadoEvent {
    def matches(chainId: GlobalChainIdMask): Boolean = ???
    def matches(id:String): Boolean = ???
  }

  val ref = actorSystem.actorOf(Props(classOf[PeerManagerActor]))

  // register for connections
  // on connection get the supported chains
  // add to database
  // check against the filters if match publish PeerQueryMatch(id:Identity)
  // get connection
  // if the connection fails try another one.
  // incoming connections get added also.

}

