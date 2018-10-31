package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Cancellable, PoisonPill, Props, SupervisorStrategy}
import sss.ancillary.Logging
import sss.asado.block.{GetLatestCommittedBlockId, GetLatestRecordedBlockId, IsSynced, NotSynchronized, Synchronized}
import sss.asado.chains.ChainSynchronizer.{StartSyncer, log}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.PeerConnection
import sss.asado._
import sss.asado.chains.LeaderElectionActor.{LeaderFound, LeaderLost, LocalLeader, RemoteLeader}
import sss.asado.chains.QuorumMonitor.QuorumLost

import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


object ChainSynchronizer extends Logging {


  def apply(chainQuorumCandidates: Set[UniqueNodeIdentifier],
            myNodeId: UniqueNodeIdentifier,
            startSyncer: StartSyncer,
            getLatestCommitted: GetLatestCommittedBlockId,
            getLatestRecorded: GetLatestRecordedBlockId,
            name:String = "ChainSynchronizer"
           )(implicit actorSystem: ActorSystem,
             chainId: GlobalChainIdMask,
             eventMessageBus: MessageEventBus
           ) = new ChainSynchronizer(

    chainQuorumCandidates,
    myNodeId,
    startSyncer,
    getLatestCommitted,
    getLatestRecorded,
    s"${name}_${chainId}"
  )

  type StartSyncer = ActorContext => ActorRef

}

class ChainSynchronizer private(chainQuorumCandidates: Set[UniqueNodeIdentifier],
                                myNodeId: UniqueNodeIdentifier,
                                startSyncer: StartSyncer,
                                getLatestCommitted: GetLatestCommittedBlockId,
                                getLatestRecorded: GetLatestRecordedBlockId,
                                name: String
                    )(implicit actorSystem: ActorSystem,
                      chainId: GlobalChainIdMask,
                      eventMessageBus: MessageEventBus
) {

  final protected val ref = actorSystem.actorOf(Props(SynchronizationActor),
    name)

  final private case object StartSync

  val maxWaitInterval: Long = 30 * 1000

  private lazy val stream: Stream[Long] = {
    (10l) #:: (20l) #:: stream.zip(stream.tail).map { n =>
     val fib = n._1 + n._2
     if (fib > maxWaitInterval) maxWaitInterval
     else fib
   }
  }

  private case class PeerConnectionTimestamped(peer:PeerConnection, whenLastUsed: Long = 0, noJoyCount: Int = 0) {
    def reset(): PeerConnectionTimestamped = copy(whenLastUsed = 0, noJoyCount = 0)
    def update(): PeerConnectionTimestamped = this.copy(noJoyCount = noJoyCount + 1, whenLastUsed = System.currentTimeMillis())
    def timeLeftToWait(nowMs: Long): Long = {
      val delay = stream(noJoyCount)
      whenLastUsed + delay - nowMs
    }
  }

  def startSync: Unit = ref ! StartSync

  private[chains] def shutdown = {
    eventMessageBus.unsubscribe(ref)
    ref ! PoisonPill
  }

  private object SynchronizationActor extends Actor {

    private val syncerRef = startSyncer(context)

    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    eventMessageBus.subscribe(classOf[ConnectionLost])
    eventMessageBus.subscribe(classOf[QuorumLost])
    eventMessageBus.subscribe(classOf[LeaderFound])
    eventMessageBus.subscribe(classOf[LeaderLost])
    eventMessageBus.subscribe(classOf[PeerConnection])

    private var inProgress = false

    private var startSyncTimer: Option[Cancellable] = None

    private var synchronised: IsSynced = {
      val bId = getLatestCommitted()
      if (chainQuorumCandidates == Set(myNodeId)) Synchronized(chainId, bId.blockHeight, bId.txIndex, myNodeId)
      else NotSynchronized(chainId)
    }

    private var connectedPeers: Seq[PeerConnectionTimestamped] = Seq()
    private var synchronizingPeerOpt: Option[PeerConnection] = None

    private def reset() = {

      if(synchronised.isSynced) {
        synchronised = NotSynchronized(chainId)
        eventMessageBus publish synchronised
      }
      synchronizingPeerOpt = None
      log.info("inProgress now false")
      inProgress = false
      self ! StartSync
    }

    private def waitForleaderLost: Receive = {

      case LeaderLost(`chainId`, _) =>
        context become receive
        reset()
    }

    override def receive: Receive = {

      case ConnectionLost(nodeId) =>
        log.info("Connected was {} removing {}, sync peer is {}", connectedPeers, nodeId, synchronizingPeerOpt)
        connectedPeers = connectedPeers.filterNot(_.peer.nodeId == nodeId)
        if(synchronizingPeerOpt.exists(_.nodeId == nodeId)) {
          reset()
        }

      case NotSynchronized(`chainId`) => //sent from child
        synchronizingPeerOpt foreach { peer =>
          val (removed, rest) = connectedPeers.partition(_.peer.nodeId == peer.nodeId)
          removed.headOption foreach { found =>
            connectedPeers = rest :+ found.update()
          }
        }
        reset()

      case s @ Synchronized(`chainId`, _, _, _) => //sent from child
        // this comes from child actor
        log.info("Now synchronized to {} with {}", s, synchronizingPeerOpt)
        synchronised = s
        inProgress = false
        eventMessageBus.publish(synchronised)
        //in the case a conn is lost before processing this message.
        synchronizingPeerOpt.getOrElse(reset())

      case _: QuorumLost => reset()

      case LocalLeader(`chainId`, leader, height, index, _) =>
        inProgress = false
        synchronised = Synchronized(chainId, height, index, leader)
        log.info("Local Leader {} hence synchronized to {}", myNodeId, synchronised)
        context become waitForleaderLost
        eventMessageBus publish synchronised


      case RemoteLeader(`chainId`, leader,_ ) =>
        val (leaderRemoved, rest) = connectedPeers.partition(_.peer.nodeId == leader)
        connectedPeers = leaderRemoved.headOption
            .map {
              self ! StartSync
              _.reset() +: rest
            }.getOrElse(connectedPeers)


      case QueryStatus =>
        val status = Status(synchronised)
        eventMessageBus.publish(status)


      case StartSync =>
        (inProgress, synchronised.isSynced) match {

          case (false, false) =>

            connectedPeers.headOption foreach { tracker =>
              val timeToWait = tracker.timeLeftToWait(System.currentTimeMillis())
              if (timeToWait <= 0) {
                syncerRef ! tracker.peer
                inProgress = true
                synchronizingPeerOpt = Option(tracker.peer)
              } else {
                startSyncTimer = Option(context
                  .system
                  .scheduler
                  .scheduleOnce(timeToWait millis,
                    self,
                    StartSync
                  )
                )
              }
            }
          case (_, _) =>
        }


      case p@PeerConnection(nodeId, caps) if caps.contains(chainId) =>
        val removeOld = connectedPeers.filterNot(_.peer.nodeId == nodeId)
        connectedPeers = PeerConnectionTimestamped(p) +: removeOld
        startSyncTimer map(_.cancel())
        self ! StartSync

    }
  }
}


