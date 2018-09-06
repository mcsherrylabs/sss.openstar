package sss.asado.chains

import akka.actor.{Actor, ActorSystem, Props}
import sss.asado.AsadoEvent
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.network.{Connection, ConnectionLost, MessageEventBus, UniqueNodeIdentifier}

object QuorumMonitor {
  case class Quorum(chainId: GlobalChainIdMask) extends AsadoEvent
  case class QuorumLost(chainId: GlobalChainIdMask) extends AsadoEvent
}

class QuorumMonitor(eventMessageBus: MessageEventBus, chain: Chain)(implicit actorSystem: ActorSystem) {

  val chainId: GlobalChainIdMask = chain.id
  def quorum(): Option[Seq[UniqueNodeIdentifier]] = None

  actorSystem.actorOf(Props(QuorumMonitorActor))

  object QuorumMonitorActor extends Actor {

    eventMessageBus.subscribe(classOf[Connection])
    eventMessageBus.subscribe(classOf[ConnectionLost])

    var connectedMembers: Set[UniqueNodeIdentifier] = Set()

    override def receive: Receive = {
      case c @ Connection(nodeId: UniqueNodeIdentifier) =>
        val currentMembers = chain.quorumMembers()
        if(currentMembers.contains(nodeId)) {
          //we're interested
          connectedMembers += c.nodeId
        }
        if(connectedMembers.size >= currentMembers.size / 2 + 1) {
          eventMessageBus.publish(Quorum(chainId))
        }

      case ConnectionLost(nodeId: UniqueNodeIdentifier) =>
        connectedMembers -= nodeId
        val currentMembers = chain.quorumMembers()

        if(connectedMembers.size == currentMembers.size / 2) {
          eventMessageBus.publish(QuorumLost(chainId))
        }
    }
  }
}


