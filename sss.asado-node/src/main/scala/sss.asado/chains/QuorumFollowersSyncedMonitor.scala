package sss.asado.chains

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.block.{BlockChain, BlockChainSignatures, BlockChainTxConfirms, Synchronized, VoteLeader}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor.{LeaderFound, LeaderLost, LocalLeader, RemoteLeader}
import sss.asado.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.asado.ledger._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db


/**
  * Created by alan on 3/18/16.
  */
object QuorumFollowersSyncedMonitor {

  case class BlockChainReady(chainId: GlobalChainIdMask,
                             leader: UniqueNodeIdentifier) extends AsadoEvent

  def apply(
            thisNodeId: UniqueNodeIdentifier,
            bc: BlockChain with BlockChainTxConfirms with BlockChainSignatures,
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
      )
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
  messageEventBus.subscribe(classOf[LeaderFound])


  private def remoteLeader(leader:UniqueNodeIdentifier): Receive = leaderLost(leader) orElse {

    case s@Synchronized(`chainId`, height, index) =>
      send(MessageKeys.Synchronized, s, leader)
      messageEventBus.publish(BlockChainReady(chainId, leader))

  }

  private def weAreLeader(followersToWaitFor: Seq[VoteLeader], height: Long, index: Long): Receive = leaderLost(thisNodeId) orElse {

    case IncomingMessage(`chainId`,
              MessageKeys.Synchronized,
              nodeId,
              s@Synchronized(`chainId`, followerHeight, followerIndex)) =>

      followersToWaitFor.find(_.nodeId == nodeId) match {

        case None => log.warning(s"got message from nodeId we are not waiting for $nodeId ($s)")

        case Some(follower) if(followerHeight == height && followerIndex == index) =>
          val newFollowersToWaitFor = followersToWaitFor.filterNot(_.nodeId == nodeId)

          if(newFollowersToWaitFor.isEmpty) messageEventBus.publish(BlockChainReady(chainId, thisNodeId))

          context become weAreLeader(newFollowersToWaitFor, height, index)

        case Some(follower) =>
          log.error(s"Follower claims it synchronised but is not $follower, required height, index $height, $index")

      }

  }

  private def waitForLeader: Receive = {

    case RemoteLeader(`chainId`, leader, members) =>
      context become remoteLeader(leader)

    case LocalLeader(`chainId`, `thisNodeId`, height, index, followers) =>
      //start listening for
      val followersToWaitFor = followers filterNot(follower => follower.height != height && follower.txIndex != index)

      if(followersToWaitFor.isEmpty) {
        messageEventBus.publish(BlockChainReady(chainId, thisNodeId))
      }
      context become weAreLeader(followersToWaitFor, height, index)
  }

  private def leaderLost(leader: UniqueNodeIdentifier): Receive = {

    case LeaderLost(`chainId`, `leader`) =>
      context become waitForLeader

  }


  override def receive: Receive = waitForLeader

}
