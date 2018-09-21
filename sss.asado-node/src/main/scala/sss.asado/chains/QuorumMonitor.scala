package sss.asado.chains

import akka.actor.{Actor, ActorRef, ActorSystem, Props, SupervisorStrategy}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado._
import sss.asado.chains.QuorumMonitor.{NotQuorumCandidate, Quorum, QuorumLost}
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerQuery
import sss.asado.peers.PeerManager.{IdQuery, PeerConnection}
import sss.asado.quorumledger.QuorumLedger.NewQuorumCandidates


object QuorumMonitor {

  object QuorumLost {
    def apply(chainId: GlobalChainIdMask): QuorumLost = new QuorumLost(chainId)
    def unapply(arg: QuorumLost): Option[GlobalChainIdMask] = Option(arg.chainId)
  }

  case class Quorum(chainId: GlobalChainIdMask, members: Set[UniqueNodeIdentifier], minConfirms: Int) extends AsadoEvent
  class QuorumLost(val chainId: GlobalChainIdMask) extends AsadoEvent
  case class NotQuorumCandidate(override val chainId: GlobalChainIdMask, nodeId: UniqueNodeIdentifier) extends QuorumLost(chainId)

  def apply(eventMessageBus: MessageEventBus,
            chainId: GlobalChainIdMask,
            myNodeId: UniqueNodeIdentifier,
            initialCandidates: Set[UniqueNodeIdentifier],
            peerQuery: PeerQuery
  )(implicit actorSystem: ActorSystem): QuorumMonitor = {
    new QuorumMonitor(eventMessageBus: MessageEventBus,
      chainId: GlobalChainIdMask,
      myNodeId: UniqueNodeIdentifier,
      initialCandidates,
      peerQuery)
  }
}

class QuorumMonitor private (eventMessageBus: MessageEventBus,
                             chainId: GlobalChainIdMask,
                             myNodeId: UniqueNodeIdentifier,
                             initialCandidates: Set[UniqueNodeIdentifier],
                             peerQuery: PeerQuery
                   )(implicit actorSystem: ActorSystem)  {

  private val ref: ActorRef = actorSystem.actorOf(Props(QuorumMonitorActor))

  private case object CheckInitialStatus

  def queryStatus = ref ! CheckInitialStatus //TODO add side efect brackets ()

  queryStatus

  private def removeThisNodeId(nodes: Set[UniqueNodeIdentifier]) =
    nodes.filterNot(_ == myNodeId)

  peerQuery.addQuery(IdQuery(removeThisNodeId(initialCandidates)))

  private object QuorumMonitorActor extends Actor {

    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    eventMessageBus.subscribe(classOf[PeerConnection])
    eventMessageBus.subscribe(classOf[ConnectionLost])
    eventMessageBus.subscribe(classOf[NewQuorumCandidates])


    private def minConfirms(): Int = candidates.size / 2 + 1

    private var isQuorum = initialCandidates.size == 0 || initialCandidates == Set(myNodeId)
    private var weAreMember = initialCandidates.size == 0 || initialCandidates.contains(myNodeId)
    private var candidates = initialCandidates
    private var connectedCandidates: Set[UniqueNodeIdentifier] = Set()

    private def connectedMemberCount(): Int = connectedCandidates.size + 1

    override def receive: Receive = {

      case CheckInitialStatus =>
        if(weAreMember) {
          val resp = if (isQuorum) Quorum(chainId, connectedCandidates, minConfirms())
          else QuorumLost(chainId)

          eventMessageBus.publish(resp)
        } else eventMessageBus.publish(NotQuorumCandidate(chainId, myNodeId))

      case NewQuorumCandidates(`chainId`, newCandidates) =>

        peerQuery.removeQuery(IdQuery(removeThisNodeId(candidates)))

        val wasMember = weAreMember
        weAreMember = newCandidates.contains(myNodeId)

        if(wasMember && !weAreMember) eventMessageBus.publish(NotQuorumCandidate(chainId, myNodeId))

        candidates = newCandidates
        connectedCandidates = connectedCandidates.filter(candidates.contains(_))

        val wasQuorum = isQuorum
        isQuorum = connectedMemberCount() >= minConfirms()

        if(wasQuorum && !isQuorum && wasMember && weAreMember)
          eventMessageBus.publish(QuorumLost(chainId))

        else if(!wasQuorum && isQuorum && weAreMember)
          eventMessageBus.publish(Quorum(chainId, connectedCandidates, minConfirms()))

        peerQuery.addQuery(IdQuery(removeThisNodeId(candidates)))


      case PeerConnection(nodeId, _) if candidates.contains(nodeId) =>
        //we're interested
        connectedCandidates += nodeId
        if(connectedMemberCount() >= minConfirms()) {
          isQuorum = true
          if(weAreMember) eventMessageBus.publish(Quorum(chainId, connectedCandidates, minConfirms()))
        }


      case ConnectionLost(nodeId: UniqueNodeIdentifier) =>

        connectedCandidates = connectedCandidates.filterNot(_ == nodeId)

        if(isQuorum && connectedMemberCount() == candidates.size / 2) {
          isQuorum = false
          if(weAreMember) eventMessageBus.publish(QuorumLost(chainId))
        }

    }
  }
}


