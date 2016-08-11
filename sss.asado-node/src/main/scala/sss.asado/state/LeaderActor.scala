package sss.asado.state

import akka.actor.{Actor, ActorLogging, ActorRef, ReceiveTimeout}
import akka.agent.Agent
import block._
import sss.asado.MessageKeys
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.{BlockChain, BlockChainLedger}
import sss.asado.ledger.Ledgers
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoState.QuorumState
import sss.asado.state.AsadoStateProtocol.{FindTheLeader, LeaderFound, QuorumStateEvent, RegisterStateEvents}
import sss.db.Db

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 4/1/16.
  */

class LeaderActor(thisNodeId: String,
                  quorum: Int,
                  peers: Agent[Set[Connection]],
                  messageRouter: ActorRef,
                  ncRef: ActorRef,
                  stateMachine: ActorRef,
                  bc: BlockChain)(implicit db: Db, ledgers: Ledgers) extends Actor with ActorLogging {

  messageRouter ! Register(MessageKeys.FindLeader)
  messageRouter ! Register(MessageKeys.Leader)
  messageRouter ! Register(MessageKeys.VoteLeader)
  stateMachine ! RegisterStateEvents


  log.info("Leader actor has started...")

  private def makeFindLeaderNetMsg: FindLeader = {
    val blockHeader = bc.lastBlockHeader
    val biggestCommittedTxIndex = bc.block(blockHeader.height + 1).maxMonotonicCommittedIndex
    val sigIndex = BlockSignatures(blockHeader.height).indexOfBlockSignature(thisNodeId).getOrElse(Int.MaxValue)
    FindLeader(blockHeader.height, biggestCommittedTxIndex, sigIndex, thisNodeId)
  }

  def receive = handleNoLeader(Set.empty)


  private def handleNoLeader(leaderConfirms: Set[String]): Receive = {

    case QuorumStateEvent => self ! FindTheLeader

    case FindTheLeader =>
      val findMsg = makeFindLeaderNetMsg
      log.info("Sending FindLeader to network ")
      context.setReceiveTimeout(10 seconds)
      ncRef ! SendToNetwork(NetworkMessage(MessageKeys.FindLeader, findMsg.toBytes))

    case ReceiveTimeout => self ! FindTheLeader

    case NetworkMessage(MessageKeys.FindLeader,bytes) =>
      log.info("Someone asked us to vote on a leader (FindLeader)")
      (makeFindLeaderNetMsg, bytes.toFindLeader) match {
        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId), FindLeader(hisBlkHeight, hisCommittedTxIndex, hidSigIndx, hisId)) if (hisBlkHeight > myBlockHeight) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! NetworkMessage(MessageKeys.VoteLeader, VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId), FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId))
          if (hisBlkHeight == myBlockHeight) && (hisCommittedTxIndex > myCommittedTxIndex) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! NetworkMessage(MessageKeys.VoteLeader, VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId), FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId))
          if (hisBlkHeight == myBlockHeight) &&
             (hisCommittedTxIndex == myCommittedTxIndex) &&
            (mySigIndex > hisSigIndx) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! NetworkMessage(MessageKeys.VoteLeader, VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId), FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId))
          if (hisBlkHeight == myBlockHeight) &&
            (hisCommittedTxIndex == myCommittedTxIndex) &&
            (mySigIndex == hisSigIndx) =>
          // This can only happen when there are no txs in the chain. Very special case.
          // the sigs Must have an order. They can't be the same unless there are none.
          def makeLong(str: String) = str.foldLeft(0l)((acc, e) => acc + e.toLong)
          if (makeLong(nodeId) > makeLong(hisId)) {
            log.info(s"My name is $nodeId and I'm voting for $hisId in order to get started up.")
            sender ! NetworkMessage(MessageKeys.VoteLeader, VoteLeader(nodeId).toBytes)
          }

        case (mine, his) => log.info(s"$mine is ahead of $his")
      }

    case NetworkMessage(MessageKeys.VoteLeader,bytes) =>

      val vote = bytes.toVoteLeader
      val confirms = leaderConfirms + vote.nodeId
      log.info(s"$thisNodeId got a vote from ${vote.nodeId}, now have ${confirms.size} of $quorum")
      if(confirms.size == quorum) {
        // I am the leader.
        context.setReceiveTimeout(Duration.Undefined)
        context.become(handle(thisNodeId))
        log.info(s"The leader is $thisNodeId (me), telling the network...")
        stateMachine ! LeaderFound(thisNodeId)
        ncRef ! SendToNetwork(NetworkMessage(MessageKeys.Leader,Leader(thisNodeId).toBytes))

        /*log.info(s"The leader is $thisNodeId (me), closing partial block.")
        Try(BlockChainLedger(bc.lastBlockHeader.height + 1).commit) match {
          case Failure(e) => log.error(e, s"Failed to commit outstanding txs in partial block")
          case Success(numTxs) =>
            log.info(s"Committed the outstanding txs ($numTxs) in partial block")
            stateMachine ! LeaderFound(thisNodeId)
            ncRef ! SendToNetwork(NetworkMessage(MessageKeys.Leader,Leader(thisNodeId).toBytes))
        }*/

      } else context.become(handleNoLeader(confirms))

    case NetworkMessage(MessageKeys.Leader,bytes) =>
      val leader = bytes.toLeader
      context.setReceiveTimeout(Duration.Undefined)
      context.become(handle(leader.nodeId))
      log.info(s"The leader is ${leader.nodeId}")
      stateMachine ! LeaderFound(leader.nodeId)
  }

  private def handle(leader: String): Receive = {

    case NetworkMessage(MessageKeys.FindLeader,bytes) => if(leader == thisNodeId) sender() ! NetworkMessage(MessageKeys.Leader,Leader(leader).toBytes)
    case NetworkMessage(MessageKeys.VoteLeader,bytes) => log.info(s"Got an surplus vote from ${bytes.toVoteLeader.nodeId}, leader is $leader")
    case NetworkMessage(MessageKeys.Leader,bytes) => log.info(s"Got an unneeded leader indicator ${bytes.toLeader.nodeId}, leader is $leader")

    case QuorumStateEvent => self ! FindTheLeader

    case FindTheLeader =>
      log.info("Sending FindLeader to myself.")
      context.become(handleNoLeader(Set.empty))
      self ! FindTheLeader

    //case x => log.info(s"Spurious leadership message $x")
  }
}
