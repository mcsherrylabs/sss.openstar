package sss.asado.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, SupervisorStrategy}
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

  private val ref: ActorRef = actorSystem.actorOf(Props(QuorumMonitorActor), s"QuorumMonitorActor_$chainId")

  private case object CheckInitialStatus

  ref ! CheckInitialStatus

  private def removeThisNodeId(nodes: Set[UniqueNodeIdentifier]) =
    nodes.filterNot(_ == myNodeId)

  peerQuery.addQuery(IdQuery(removeThisNodeId(initialCandidates)))

  private object QuorumMonitorActor extends Actor with ActorLogging {

    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    eventMessageBus.subscribe(classOf[PeerConnection])
    eventMessageBus.subscribe(classOf[ConnectionLost])
    eventMessageBus.subscribe(classOf[NewQuorumCandidates])


    private def minConfirms(): Int = candidates.size / 2

    private var isQuorum = initialCandidates == Set(myNodeId)
    private var weAreMember = initialCandidates.contains(myNodeId)
    private var candidates = initialCandidates
    private var connectedCandidates: Set[UniqueNodeIdentifier] = Set()

    private def connectedMemberCount(): Int = connectedCandidates.size

    override def receive: Receive = {

      case CheckInitialStatus =>
        if (weAreMember && isQuorum) {
          eventMessageBus.publish(Quorum(chainId, connectedCandidates, minConfirms()))
        }

      case NewQuorumCandidates(`chainId`, newCandidates) =>

        peerQuery.removeQuery(IdQuery(removeThisNodeId(candidates)))

        val wasMember = weAreMember
        weAreMember = newCandidates.contains(myNodeId)

        candidates = newCandidates
        connectedCandidates = connectedCandidates.filter(candidates.contains(_))

        val wasQuorumConnections = isQuorum
        isQuorum = connectedMemberCount() >= minConfirms()

        (wasQuorumConnections, isQuorum, wasMember, weAreMember) match {

          case (false, true, _, true) =>
            eventMessageBus.publish(Quorum(chainId, connectedCandidates, minConfirms()))

          case (_, true, false, true) =>
            eventMessageBus.publish(Quorum(chainId, connectedCandidates, minConfirms()))

          case (true, false, _, true) =>
            eventMessageBus.publish(QuorumLost(chainId))

          case (true, _, true, false) =>
            eventMessageBus.publish(NotQuorumCandidate(chainId, myNodeId))

          case x =>
            log.debug("Unmatched dealing with NewQuorumCandidates{}, quorum is {}", x, isQuorum)

        }


        peerQuery.addQuery(IdQuery(removeThisNodeId(candidates)))


      case PeerConnection(nodeId, _) if candidates.contains(nodeId) =>
        //we're interested
        connectedCandidates += nodeId
        if (connectedMemberCount() >= minConfirms()) {
          isQuorum = true
          if (weAreMember) eventMessageBus.publish(Quorum(chainId, connectedCandidates, minConfirms()))
        }



      case ConnectionLost(nodeId: UniqueNodeIdentifier) =>

        connectedCandidates = connectedCandidates.filterNot(_ == nodeId)

        if(isQuorum && connectedMemberCount() == candidates.size / 2 - 1) {
          isQuorum = false
          if(weAreMember) eventMessageBus.publish(QuorumLost(chainId))
        }

    }
  }
}


