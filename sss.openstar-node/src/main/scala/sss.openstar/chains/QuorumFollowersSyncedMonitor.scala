package sss.openstar.chains


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.openstar.block.BlockChainLedger.NewBlockId
import sss.openstar.block.{Synchronized, VoteLeader}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.chains.LeaderElectionActor.{LeaderFound, LeaderLost, LocalLeader, RemoteLeader}
import sss.openstar.chains.QuorumFollowersSyncedMonitor.{BlockChainReady, QuorumSync, SyncedQuorum}
import sss.openstar.common.block.BlockId
import sss.openstar.ledger._
import sss.openstar.network.MessageEventBus.IncomingMessage
import sss.openstar.network._
import sss.openstar.{OpenstarEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db
import scala.language.postfixOps

/**
  * Created by alan on 3/18/16.
  */
object QuorumFollowersSyncedMonitor {

  type QuorumSyncs = Seq[QuorumSync]

  case class SyncedQuorum(chainId: GlobalChainIdMask, syncs: QuorumSyncs, minConfirms: Int) extends OpenstarEvent {
    val members: Set[UniqueNodeIdentifier] = syncs map (_.nodeId) toSet
  }

  case class QuorumSync(nodeId: UniqueNodeIdentifier, height: Long, index: Long)

  /* Issued by every node *except* the leader to indicate
     the node has found a leader and is ready to process txs
   */
  case class BlockChainReady(chainId: GlobalChainIdMask,
                             leader: UniqueNodeIdentifier) extends OpenstarEvent

  def apply(
             thisNodeId: UniqueNodeIdentifier,
             disconnect: UniqueNodeIdentifier => Unit
            )(implicit actorSystem: ActorSystem,
              db: Db,
              chainId: GlobalChainIdMask,
              send: Send,
              messageEventBus: MessageEventBus,
              ledgers: Ledgers
                             ): ActorRef = {

      actorSystem.actorOf(Props(classOf[QuorumFollowersSyncedMonitor],
        thisNodeId,
        disconnect,
        db,
        ledgers,
        chainId,
        send,
        messageEventBus)
      , s"QuorumFollowersSyncedMonitor_$chainId")
  }
}

private class QuorumFollowersSyncedMonitor(
               thisNodeId: UniqueNodeIdentifier,
               disconnect: UniqueNodeIdentifier => Unit
               )(implicit val db: Db,
                 ledgers: Ledgers,
                 chainId: GlobalChainIdMask,
                 send: Send,
                 messageEventBus: MessageEventBus) extends Actor with ActorLogging {

  log.info("QuorumFollowersSyncedMonitor actor has started...")


  messageEventBus.subscribe(classOf[NewBlockId])
  messageEventBus.subscribe(classOf[LeaderLost])
  messageEventBus.subscribe(classOf[Synchronized])
  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(MessageKeys.Synchronized)
  messageEventBus.subscribe(classOf[LeaderFound])


  private def remoteLeader(leader:UniqueNodeIdentifier): Receive = leaderLost(leader) orElse {

    case s@Synchronized(`chainId`, height, index, _) =>
      areWeSynced = Option(s)
      send(MessageKeys.Synchronized, s, leader)
      messageEventBus.publish(BlockChainReady(chainId, leader))

  }

  private def checkForSyncedQuorum(followers: Seq[VoteLeader], minConfirms: Int) = {
    val minusSynced = followers.filterNot(follower => syncedFollowers.contains(follower.nodeId))
    if (minusSynced.isEmpty) {
      val sq = SyncedQuorum(chainId, syncedFollowers map (follower => QuorumSync(follower._1, follower._2.height, follower._2.index)) toSeq, minConfirms)
      messageEventBus publish sq
    }
  }

  private def weAreLeader(followers: Seq[VoteLeader], height: Long, index: Long, minConfirms: Int): Receive = leaderLost(thisNodeId) orElse {

    // it possible a connection is lot during wait for sync
    // but this might not result in QuorumLost of Leader lost.
    case ConnectionLost(nodeId) =>
      syncedFollowers = syncedFollowers filterNot (_._1 == nodeId)
      context become weAreLeader(followers.filterNot(_.nodeId == nodeId), height, index, minConfirms)
      checkForSyncedQuorum(followers, minConfirms)

    case NewBlockId(`chainId`, BlockId(blockHeight, txIndex)) =>
      context become weAreLeader(followers, blockHeight, txIndex, minConfirms)

    case IncomingMessage(`chainId`,
              MessageKeys.Synchronized,
              nodeId,
              s@Synchronized(`chainId`, followerHeight, followerIndex, _)) =>

      if (followerHeight != height || followerIndex != index + 1) {

        log.warning(s"Follower $nodeId is 'synchronized' but height {} and index {} don't match leaders {} {} - disconnect!",
          followerHeight, followerIndex, height, index)
        disconnect(nodeId)

      } else {

        syncedFollowers += nodeId -> s
        checkForSyncedQuorum(followers, minConfirms)

      }

  }

  private var areWeSynced: Option[Synchronized] = None
  private var syncedFollowers: Map[UniqueNodeIdentifier, Synchronized] = Map()


  private def waitForLeader: Receive = {

    case IncomingMessage(`chainId`, MessageKeys.Synchronized,  nodeId,  s@Synchronized(`chainId`, _, _,_)) =>
      syncedFollowers += nodeId -> s

    case s@Synchronized(`chainId`, _, _, _) =>
      areWeSynced = Option(s)

    case RemoteLeader(`chainId`, leader, members) =>
      context become remoteLeader(leader)
      areWeSynced foreach { s =>
        send(MessageKeys.Synchronized, s, leader)
        messageEventBus.publish(BlockChainReady(chainId, leader))
      }

    case LocalLeader(`chainId`, `thisNodeId`, height, index, followers, min) =>

      checkForSyncedQuorum(followers, min)
      context become weAreLeader(followers, height, index, min)
  }


  private def leaderLost(leader: UniqueNodeIdentifier): Receive = {

    case LeaderLost(`chainId`, `leader`) =>
      context become waitForLeader
      syncedFollowers = Map()

  }


  override def receive: Receive = waitForLeader

}
