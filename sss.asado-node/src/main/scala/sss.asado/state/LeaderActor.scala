package sss.asado.state

import akka.actor.{Actor, ActorLogging, ActorRef, ReceiveTimeout}
import sss.asado.block._
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.block.{BlockChain, BlockChainLedger}
import sss.asado.block.signature.BlockSignatures
import sss.asado.ledger.Ledgers
import sss.asado.network.{MessageEventBus, _}
import sss.asado.state.AsadoStateProtocol.QuorumStateEvent
import sss.asado.state.LeaderActor.{FindTheLeader, LeaderFound}
import sss.db.Db

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 4/1/16.
  */
object LeaderActor {

  case class LeaderFound(leader: String)

  private case object FindTheLeader

}

class LeaderActor(thisNodeId: String,
                  quorum: Set[UniqueNodeIdentifier],
                  messageBus: MessageEventBus,
                  ncRef: NetworkRef,
                  stateMachine: ActorRef,
                  bc: BlockChain)(implicit db: Db, ledgers: Ledgers)
    extends Actor
    with ActorLogging
    with AsadoEventSubscribedActor {

  assert(quorum.size > 0, "have you wired up the quorum?")
  def receive = ???

  /*messageBus.subscribe(MessageKeys.FindLeader)
  messageBus.subscribe(MessageKeys.Leader)
  messageBus.subscribe(MessageKeys.VoteLeader)

  log.info("Leader actor has started...")

  private def makeFindLeaderNetMsg: FindLeader = {
    val blockHeader = bc.lastBlockHeader
    val biggestCommittedTxIndex =
      bc.block(blockHeader.height + 1).maxMonotonicCommittedIndex
    val sigIndex = BlockSignatures(blockHeader.height)
      .indexOfBlockSignature(thisNodeId)
      .getOrElse(Int.MaxValue)
    FindLeader(blockHeader.height,
               biggestCommittedTxIndex,
               sigIndex,
               thisNodeId)
  }

  def receive = handleNoLeader(Set.empty)

  private def handleNoLeader(leaderConfirms: Set[String]): Receive = {

    case QuorumStateEvent => self ! FindTheLeader

    case FindTheLeader =>
      val findMsg = makeFindLeaderNetMsg
      log.info("Sending FindLeader to network ")
      context.setReceiveTimeout(10 seconds)
      ncRef.send(SerializedMessage(MessageKeys.FindLeader, findMsg.toBytes), quorum)

    case ReceiveTimeout => self ! FindTheLeader

    case SerializedMessage(_, MessageKeys.FindLeader, bytes) =>
      log.info("Someone asked us to vote on a leader (FindLeader)")
      (makeFindLeaderNetMsg, bytes.toFindLeader) match {
        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId),
              FindLeader(hisBlkHeight, hisCommittedTxIndex, hidSigIndx, hisId))
            if (hisBlkHeight > myBlockHeight) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! SerializedMessage(MessageKeys.VoteLeader,
                                  VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId),
              FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId))
            if (hisBlkHeight == myBlockHeight) && (hisCommittedTxIndex > myCommittedTxIndex) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! SerializedMessage(MessageKeys.VoteLeader,
                                  VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId),
              FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId))
            if (hisBlkHeight == myBlockHeight) &&
              (hisCommittedTxIndex == myCommittedTxIndex) &&
              (mySigIndex > hisSigIndx) =>
          // I vote for him
          log.info(s"My name is $nodeId and I'm voting for $hisId")
          sender ! SerializedMessage(MessageKeys.VoteLeader,
                                  VoteLeader(nodeId).toBytes)

        case (FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId),
              FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId))
            if (hisBlkHeight == myBlockHeight) &&
              (hisCommittedTxIndex == myCommittedTxIndex) &&
              (mySigIndex == hisSigIndx) =>
          // This can only happen when there are no txs in the chain. Very special case.
          // the sigs Must have an order. They can't be the same unless there are none.
          def makeLong(str: String) =
            str.foldLeft(0l)((acc, e) => acc + e.toLong)
          if (makeLong(nodeId) > makeLong(hisId)) {
            log.info(
              s"My name is $nodeId and I'm voting for $hisId in order to get started up.")
            sender ! SerializedMessage(MessageKeys.VoteLeader,
                                    VoteLeader(nodeId).toBytes)
          }

        case (mine, his) => log.info(s"$mine is ahead of $his")
      }

    case SerializedMessage(_, MessageKeys.VoteLeader, bytes) =>
      val vote = bytes.toVoteLeader
      val confirms = leaderConfirms + vote.nodeId
      log.info(
        s"$thisNodeId got a vote from ${vote.nodeId}, now have ${confirms.size} of $quorum")
      if (confirms.size == quorum.size) {
        // I am the leader.
        context.setReceiveTimeout(Duration.Undefined)
        context.become(handle(thisNodeId))
        log.info(
          s"The leader is $thisNodeId (me), committing outstanding txs...")
        Try(BlockChainLedger(bc.lastBlockHeader.height + 1).commit) match {
          case Failure(e) =>
            log.error(e, s"Failed to commit outstanding txs in partial block")
          case Success(numTxs) =>
            log.info(
              s"Committed the outstanding txs ($numTxs) in partial block")
            stateMachine ! LeaderFound(thisNodeId)
            ncRef.send(SerializedMessage(1. toByte, MessageKeys.Leader, Leader(thisNodeId).toBytes), "NOWHERE")
        }

      } else context.become(handleNoLeader(confirms))

    case SerializedMessage(_, MessageKeys.Leader, bytes) =>
      val leader = bytes.toLeader
      context.setReceiveTimeout(Duration.Undefined)
      context.become(handle(leader.nodeId))
      log.info(s"The leader is ${leader.nodeId}")
      stateMachine ! LeaderFound(leader.nodeId)
  }

  private def handle(leader: String): Receive = {

    case SerializedMessage(_, MessageKeys.FindLeader, bytes) =>
      if (leader == thisNodeId)
        sender() ! SerializedMessage(1.toByte, MessageKeys.Leader, Leader(leader).toBytes)
    case SerializedMessage(_, MessageKeys.VoteLeader, bytes) =>
      log.info(
        s"Got an surplus vote from ${bytes.toVoteLeader.nodeId}, leader is $leader")
    case SerializedMessage(_, MessageKeys.Leader, bytes) =>
      log.info(
        s"Got an unneeded leader indicator ${bytes.toLeader.nodeId}, leader is $leader")

    case QuorumStateEvent => self ! FindTheLeader

    case FindTheLeader =>
      log.info("Sending FindLeader to myself.")
      context.become(handleNoLeader(Set.empty))
      self ! FindTheLeader

    //case x => log.info(s"Spurious leadership message $x")
  }*/
}
