package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props, SupervisorStrategy}
import sss.asado.block.Synchronized
import sss.asado.chains.ChainSynchronizer.{NotSynchronized, StartSyncer}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.PeerConnection
import sss.asado._

import scala.util.Random

object ChainSynchronizer {

  def apply(chainQuorumCandidates: Set[UniqueNodeIdentifier],
            myNodeId: UniqueNodeIdentifier,
            startSyncer: StartSyncer,
           )(implicit actorSystem: ActorSystem,
             chainId: GlobalChainIdMask,
             eventMessageBus: MessageEventBus
           ) = new ChainSynchronizer(chainQuorumCandidates, myNodeId, startSyncer)

  type StartSyncer = (ActorContext, PeerConnection) => Unit

  case class NotSynchronized(chainIdMask: GlobalChainIdMask, nodeId: UniqueNodeIdentifier)
}

class ChainSynchronizer private(chainQuorumCandidates: Set[UniqueNodeIdentifier],
                        myNodeId: UniqueNodeIdentifier,
                        startSyncer: StartSyncer,
                    )(implicit actorSystem: ActorSystem,
                      chainId: GlobalChainIdMask,
                      eventMessageBus: MessageEventBus
) extends QueryStatusSupport {

  final protected val ref = actorSystem.actorOf(Props(SynchronizationActor),
    s"Synchronization_${chainId}_${Random.nextLong()}")

  private case object StartSync


  def startSync: Unit = ref ! StartSync

  private object SynchronizationActor extends Actor {

    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    eventMessageBus.subscribe(classOf[PeerConnection])

    private var inProgress = false


    private var synchronised =
      if(chainQuorumCandidates.size == 0 || chainQuorumCandidates == Set(myNodeId)) Option(Synchronized(chainId, 0,0 ))
      else None

    private var connectedPeers: Seq[PeerConnection] = Seq()


    override def receive: Receive = waitForConnection

    private def startSyncing(connection: PeerConnection): Receive = waitForConnection orElse {

      case NotSynchronized(`chainId`, nodeId) if(nodeId == connection.nodeId) =>
        connectedPeers = connectedPeers.tail :+ connection
        context become waitForConnection
        inProgress = false
        self ! StartSync
    }


    private def waitForConnection: Receive = {

      case QueryStatus =>
        val status = Status(synchronised)
        eventMessageBus.publish(status)

      case Synchronized(`chainId`, height, index) =>
        synchronised = Option(Synchronized(chainId, height, index))
        inProgress = false
        eventMessageBus.publish(synchronised.get)

      case StartSync =>
        if(!inProgress && synchronised.isEmpty) {
          connectedPeers.headOption map { peer =>
            startSyncer(context, peer)
            inProgress = true
            context become (startSyncing(peer) orElse waitForConnection)
          }
        }

      case p@PeerConnection(nodeId, caps) if(caps.contains(chainId))=>
        if(!connectedPeers.contains(nodeId)) {
          connectedPeers =  p +: connectedPeers
          self ! StartSync
        }


    }
  }
}


