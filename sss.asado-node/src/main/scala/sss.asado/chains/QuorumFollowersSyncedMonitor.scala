package sss.asado.chains

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.block.{BlockChain, BlockChainSignatures, BlockChainTxConfirms, Synchronized, VoteLeader}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor.{LeaderFound, WeAreLeader}
import sss.asado.chains.QuorumMonitor.{NotQuorumCandidate, QuorumLost}
import sss.asado.chains.QuorumFollowersSyncedMonitor.LocalBlockChainUp

import sss.asado.ledger. _
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.{AsadoEvent, MessageKeys, UniqueNodeIdentifier}
import sss.db.Db


/**
  * Created by alan on 3/18/16.
  */
object QuorumFollowersSyncedMonitor {

  case class LocalBlockChainUp(chainId: GlobalChainIdMask,
                               nodeId: UniqueNodeIdentifier) extends AsadoEvent

  def apply(messageEventBus: MessageEventBus,
            thisNodeId: UniqueNodeIdentifier,
            bc: BlockChain with BlockChainTxConfirms with BlockChainSignatures,
            send: NetSendTo)(implicit actorSystem: ActorSystem,
                             db: Db,
                             ledgers: Ledgers,
                             chainId: GlobalChainIdMask): ActorRef = {

      actorSystem.actorOf(Props(classOf[QuorumFollowersSyncedMonitor],
        messageEventBus,
        thisNodeId,
        send,
        db,
        ledgers,
        chainId)
      )
  }
}

private class QuorumFollowersSyncedMonitor(
               messageEventBus: MessageEventBus,
               thisNodeId: UniqueNodeIdentifier,
               send: NetSendTo)(implicit val db: Db, ledgers: Ledgers, chainId: GlobalChainIdMask) extends Actor with ActorLogging {

  log.info("QuorumFollowersSyncedMonitor actor has started...")


  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[Synchronized])
  messageEventBus.subscribe(classOf[WeAreLeader])
  messageEventBus.subscribe(classOf[LeaderFound])
  messageEventBus.subscribe(classOf[NotQuorumCandidate])
  messageEventBus.subscribe(classOf[QuorumLost])

  private def remoteLeader(leader:UniqueNodeIdentifier): Receive = {

    case s@Synchronized(`chainId`, height, index) =>

      send(SerializedMessage(MessageKeys.Synchronized, s.toBytes), leader)

    case ConnectionLost(`leader`) => //TODO FIX ME <--- USE QUORUM
      context become waitForLeader

  }

  private def weAreLeader(followersToWaitFor: Seq[VoteLeader], height: Long, index: Long): Receive = {

    case QuorumLost(`chainId`) =>
      context become waitForLeader

    case NotQuorumCandidate(`chainId`, `thisNodeId`) =>
      context become waitForLeader


    case IncomingMessage(`chainId`,
              MessageKeys.Synchronized,
              nodeId,
              s@Synchronized(`chainId`, followerHeight, followerIndex)) =>

      followersToWaitFor.find(_.nodeId == nodeId) match {

        case None => log.warning(s"got message from nodeId we are not waiting for $nodeId ($s)")

        case Some(follower) if(followerHeight == height && followerIndex == index) =>
          val newFollowersToWaitFor = followersToWaitFor.filterNot(_.nodeId == nodeId)

          if(newFollowersToWaitFor.isEmpty) messageEventBus.publish(LocalBlockChainUp(chainId, thisNodeId))

          context become weAreLeader(newFollowersToWaitFor, height, index)

        case Some(follower) =>
          log.error(s"Follower claims it synchronised but is not $follower, required height, index $height, $index")

      }


  }

  private def waitForLeader: Receive = {

    case LeaderFound(`chainId`, leader, members) =>
      context become remoteLeader(leader)

    case WeAreLeader(`chainId`, height, index, followers) =>
      //start listening for
      val followersToWaitFor = followers filterNot(follower => follower.height != height && follower.txIndex != index)
      if(followersToWaitFor.isEmpty) messageEventBus.publish(LocalBlockChainUp(chainId, thisNodeId))
      context become weAreLeader(followersToWaitFor, height, index)
  }

  override def receive: Receive = waitForLeader

}
