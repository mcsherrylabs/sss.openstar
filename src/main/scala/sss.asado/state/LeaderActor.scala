package sss.asado.state

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.agent.Agent
import block._
import sss.asado.MessageKeys
import sss.asado.Node.InitWithActorRefs
import sss.asado.block.BlockChain
import sss.asado.block.signature.BlockSignatures
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoStateProtocol.{FindTheLeader, LeaderFound}
import sss.db.Db

/**
  * Created by alan on 4/1/16.
  */

class LeaderActor(thisNodeId: String,
                  quorum: Int,
                  peers: Agent[Set[Connection]],
                  messageRouter: ActorRef,
                  bc: BlockChain)(implicit db: Db) extends Actor with ActorLogging {

  messageRouter ! Register(MessageKeys.FindLeader)
  messageRouter ! Register(MessageKeys.Leader)
  messageRouter ! Register(MessageKeys.VoteLeader)

  private def makeFindLeaderNetMsg: FindLeader = {
    val blockHeader = bc.lastBlock
    val index = BlockSignatures(blockHeader.height).indexOfBlockSignature(thisNodeId).getOrElse(Int.MaxValue)
    FindLeader(blockHeader.height, index, thisNodeId)
  }


  def init: Receive = {
    case InitWithActorRefs(ncRef, stateMachine) =>
      context become (handleNoLeader(ncRef, stateMachine, Set.empty))
  }

  def receive = init


  private def handleNoLeader(nc: ActorRef,
                             stateMachine: ActorRef,
                             leaderConfirms: Set[String]): Receive = {

    case FindTheLeader => {
      nc ! SendToNetwork(NetworkMessage(MessageKeys.FindLeader, makeFindLeaderNetMsg.toBytes))
    }

    case NetworkMessage(MessageKeys.FindLeader,bytes) =>
      (makeFindLeaderNetMsg, bytes.toFindLeader) match {
        case (FindLeader(myBlockHeight, mySigIndex, nodeId), FindLeader(hisBlkHeight, hidSigIndx, hisId)) if(hisBlkHeight > myBlockHeight) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! NetworkMessage(MessageKeys.VoteLeader, VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, mySigIndex, nodeId), FindLeader(hisBlkHeight, hisSigIndx, hisId))
          if(hisBlkHeight == myBlockHeight) && (mySigIndex > hisSigIndx) =>
            // I vote for him
            log.info(s"My name is $nodeId and I'm voting for $hisId")
            sender ! NetworkMessage(MessageKeys.VoteLeader, VoteLeader(nodeId).toBytes)

        case (mine, his) => log.info(s"$mine is ahead of $his")
      }

    case NetworkMessage(MessageKeys.VoteLeader,bytes) =>

      val vote = bytes.toVoteLeader
      val confirms = leaderConfirms + vote.nodeId
      log.info(s"$thisNodeId got a vote from ${vote.nodeId}, now have ${confirms.size} of $quorum")
      if(confirms.size == quorum) {
        // I am the leader.
        context.become(handle(nc, stateMachine,thisNodeId))
        log.info(s"The leader is $thisNodeId (me)")
        stateMachine ! LeaderFound(thisNodeId)
        nc ! SendToNetwork(NetworkMessage(MessageKeys.Leader,Leader(thisNodeId).toBytes))

      } else context.become(handleNoLeader(nc, stateMachine, confirms))

    case NetworkMessage(MessageKeys.Leader,bytes) =>
      val leader = bytes.toLeader
      context.become(handle(nc, stateMachine,leader.nodeId))
      log.info(s"The leader is ${leader.nodeId}")
      stateMachine ! LeaderFound(leader.nodeId)
  }

  private def handle(nc: ActorRef,
                     stateMachine: ActorRef,leader: String): Receive = {

    case NetworkMessage(MessageKeys.FindLeader,bytes) => if(leader == thisNodeId) sender() ! NetworkMessage(MessageKeys.Leader,Leader(leader).toBytes)

    case NetworkMessage(MessageKeys.VoteLeader,bytes) => log.info(s"Got an surplus vote from ${bytes.toVoteLeader.nodeId}, leader is $leader")
    case NetworkMessage(MessageKeys.Leader,bytes) => log.info(s"Got an unneeded leader indicator ${bytes.toLeader.nodeId}, leader is $leader")

    case FindTheLeader =>  {
      context.become(handleNoLeader(nc, stateMachine,Set.empty))
      self forward FindTheLeader
    }
    case x => log.info(s"Spurious leadership message $x")
  }
}
