package sss.asado.chains

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.block.{BlockChain, BlockChainSignatures, BlockChainSignaturesAccessor, Synchronized, VoteLeader}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor.{LeaderFound, LeaderLost, LocalLeader, RemoteLeader}
import sss.asado.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.asado.common.block.{BlockChainTxId, BlockTxId}
import sss.asado.ledger._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db

import scala.None


/**
  * Created by alan on 3/18/16.
  */
object QuorumFollowersSyncedMonitor {

  case class BlockChainReady(chainId: GlobalChainIdMask,
                             leader: UniqueNodeIdentifier) extends AsadoEvent

  def apply(
             thisNodeId: UniqueNodeIdentifier,
             bc: BlockChain with BlockChainSignaturesAccessor,
            )(implicit actorSystem: ActorSystem,
              db: Db,
              chainId: GlobalChainIdMask,
              send: Send,
              messageEventBus: MessageEventBus,
              ledgers: Ledgers
                             ): ActorRef = {

      actorSystem.actorOf(Props(classOf[QuorumFollowersSyncedMonitor],
        thisNodeId,
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
               )(implicit val db: Db,
                 ledgers: Ledgers,
                 chainId: GlobalChainIdMask,
                 send: Send,
                 messageEventBus: MessageEventBus) extends Actor with ActorLogging {

  log.info("QuorumFollowersSyncedMonitor actor has started...")


  messageEventBus.subscribe(classOf[LeaderLost])
  messageEventBus.subscribe(classOf[Synchronized])
  messageEventBus.subscribe(MessageKeys.Synchronized)
  messageEventBus.subscribe(classOf[LeaderFound])


  private def isBlockChainReady(followers: Seq[VoteLeader],
                                leaderHeight: Long,
                                leaderIndex:Long
                               ): Option[BlockChainReady] = {

    //val minusUpToDate = followers.filterNot(follower => follower.height == leaderHeight && follower.committedTxIndex == leaderIndex)
    val minusSynced = followers.filterNot(follower => syncedFollowers.contains(follower.nodeId))

    if(minusSynced.isEmpty) Some(BlockChainReady(chainId, thisNodeId))
    else None

  }

  private def remoteLeader(leader:UniqueNodeIdentifier): Receive = leaderLost(leader) orElse {

    case s@Synchronized(`chainId`, height, index) =>
      areWeSynced = Option(s)
      send(MessageKeys.Synchronized, s, leader)
      messageEventBus.publish(BlockChainReady(chainId, leader))

  }

  private def weAreLeader(followers: Seq[VoteLeader], height: Long, index: Long): Receive = leaderLost(thisNodeId) orElse {

    case IncomingMessage(`chainId`,
              MessageKeys.Synchronized,
              nodeId,
              s@Synchronized(`chainId`, followerHeight, followerIndex)) =>

      syncedFollowers += nodeId -> s

      if (followerHeight != height || followerIndex != index + 1) {
        log.warning(s"Follower $nodeId is 'synchronized' but height {} and index {} don't match leaders {} {}",
          followerHeight, followerIndex, height, index)
      }

      isBlockChainReady(followers, height, index) foreach (messageEventBus.publish(_))

  }

  private var areWeSynced: Option[Synchronized] = None
  private var syncedFollowers: Map[UniqueNodeIdentifier, Synchronized] = Map()

  private def waitForLeader: Receive = {

    case IncomingMessage(`chainId`, MessageKeys.Synchronized,  nodeId,  s@Synchronized(`chainId`, followerHeight, followerIndex)) =>
      syncedFollowers += nodeId -> s

    case s@Synchronized(`chainId`, height, index) =>
      areWeSynced = Option(s)

    case RemoteLeader(`chainId`, leader, members) =>
      context become remoteLeader(leader)
      areWeSynced foreach { s =>
        send(MessageKeys.Synchronized, s, leader)
        messageEventBus.publish(BlockChainReady(chainId, leader))
      }

    case LocalLeader(`chainId`, `thisNodeId`, height, index, followers) =>

      isBlockChainReady(followers, height, index) foreach (messageEventBus.publish(_))
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
