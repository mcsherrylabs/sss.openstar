package sss.asado.chains


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.block.BlockChainLedger.NewBlockId
import sss.asado.block.{BlockChain, BlockChainSignatures, BlockChainSignaturesAccessor, Synchronized, VoteLeader}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor.{LeaderFound, LeaderLost, LocalLeader, RemoteLeader}
import sss.asado.chains.QuorumFollowersSyncedMonitor.{BlockChainReady, QuorumSync, SyncedQuorum}
import sss.asado.common.block.BlockId
import sss.asado.ledger._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db
import scala.language.postfixOps

/**
  * Created by alan on 3/18/16.
  */
object QuorumFollowersSyncedMonitor {

  type QuorumSyncs = Seq[QuorumSync]

  case class SyncedQuorum(chainId: GlobalChainIdMask, syncs: QuorumSyncs) extends AsadoEvent {
    val members: Set[UniqueNodeIdentifier] = syncs map (_.nodeId) toSet
    val minConfirms: Int = members.size // TODO really?
  }

  case class QuorumSync(nodeId: UniqueNodeIdentifier, height: Long, index: Long)

  /* Issued by every node *except* the leader to indicate
     the node has found a leader and is ready to process txs
   */
  case class BlockChainReady(chainId: GlobalChainIdMask,
                             leader: UniqueNodeIdentifier) extends AsadoEvent

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
  messageEventBus.subscribe(MessageKeys.Synchronized)
  messageEventBus.subscribe(classOf[LeaderFound])


  private def remoteLeader(leader:UniqueNodeIdentifier): Receive = leaderLost(leader) orElse {

    case s@Synchronized(`chainId`, height, index, _) =>
      areWeSynced = Option(s)
      send(MessageKeys.Synchronized, s, leader)
      messageEventBus.publish(BlockChainReady(chainId, leader))

  }

  private def checkForSyncedQuorum(followers: Seq[VoteLeader]) = {
    val minusSynced = followers.filterNot(follower => syncedFollowers.contains(follower.nodeId))
    if (minusSynced.isEmpty) {
      val sq = SyncedQuorum(chainId, syncedFollowers map (follower => QuorumSync(follower._1, follower._2.height, follower._2.index)) toSeq)
      messageEventBus publish sq
    }
  }

  private def weAreLeader(followers: Seq[VoteLeader], height: Long, index: Long): Receive = leaderLost(thisNodeId) orElse {

    case NewBlockId(BlockId(blockHeight, txIndex)) =>
      context become weAreLeader(followers, blockHeight, txIndex)

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
        checkForSyncedQuorum(followers)

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

    case LocalLeader(`chainId`, `thisNodeId`, height, index, followers) =>

      checkForSyncedQuorum(followers)
      context become weAreLeader(followers, height, index)
  }


  private def leaderLost(leader: UniqueNodeIdentifier): Receive = {

    case LeaderLost(`chainId`, `leader`) =>
      context become waitForLeader
      syncedFollowers = Map()
      areWeSynced = None //TODO check this?

  }


  override def receive: Receive = waitForLeader

}
