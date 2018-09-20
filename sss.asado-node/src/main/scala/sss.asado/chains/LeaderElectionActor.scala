package sss.asado.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import sss.asado.block._
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor.{FindTheLeader, LeaderFound, MakeFindLeader, WeAreLeader}
import sss.asado.chains.QuorumMonitor.{NotQuorumCandidate, Quorum, QuorumLost}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.{MessageEventBus, _}
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by alan on 4/1/16.
  */
object LeaderElectionActor {

  type MakeFindLeader = () => FindLeader


  case class WeAreLeader(chainId: GlobalChainIdMask,
                         height: Long,
                         index:Long,
                         followers: Seq[VoteLeader]) extends AsadoEvent

  case class LeaderFound(chainId: GlobalChainIdMask,
                         leader: String,
                         members: Set[UniqueNodeIdentifier]) extends AsadoEvent

  private case object FindTheLeader


  private def createFindLeaderMsg(thisNodeId: UniqueNodeIdentifier,
                                   bc: BlockChain with BlockChainSignatures)(implicit db: Db): FindLeader = {

    val blockHeader = bc.lastBlockHeader
    val biggestCommittedTxIndex =
      bc.block(blockHeader.height + 1).maxMonotonicCommittedIndex

    val sigIndex = bc.indexOfBlockSignature(blockHeader.height, thisNodeId)
      .getOrElse(Int.MaxValue)

    FindLeader(blockHeader.height,
      biggestCommittedTxIndex,
      sigIndex,
      thisNodeId)
  }

  def apply(thisNodeId: UniqueNodeIdentifier,

            createFindLeader: MakeFindLeader)
           (implicit  chainId: GlobalChainIdMask,
            messageBus: MessageEventBus,
            send: Send,
            actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(Props(classOf[LeaderElectionActor],
      thisNodeId,
      messageBus,
      send,
      createFindLeader,
      chainId), s"LeaderActor_$chainId")
  }


    def apply(thisNodeId: UniqueNodeIdentifier,
            bc: BlockChain with BlockChainSignatures)
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageBus: MessageEventBus,
            actorSystem: ActorSystem): ActorRef = {

    def createFindLeader: MakeFindLeader = () => createFindLeaderMsg(thisNodeId, bc)
    apply(thisNodeId, createFindLeader)
  }
}

private class LeaderElectionActor(
                  thisNodeId: UniqueNodeIdentifier,
                  messageBus: MessageEventBus,
                  send: Send,
                  createFindLeader: MakeFindLeader)(implicit chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging {

  messageBus.subscribe(classOf[Quorum])
  messageBus.subscribe(classOf[QuorumLost])
  messageBus.subscribe(classOf[ConnectionLost])
  messageBus.subscribe(MessageKeys.FindLeader)
  messageBus.subscribe(MessageKeys.Leader)
  messageBus.subscribe(MessageKeys.VoteLeader)

  log.info("Leader actor has started...")


  private var leaderVotes: Seq[VoteLeader] = Seq.empty

  def receive = waitForQuorum

  private def waitForQuorum: Receive = {

    case Quorum(`chainId`, members, _)
      if(members.size == 0 || members == Set(thisNodeId)) =>
      //Special case where there is a quorum and it's just us or no one.
      messageBus publish WeAreLeader(chainId, 0, 0, Seq())

    case Quorum(`chainId`, members, _) =>
      context become findLeader(members)
      self ! FindTheLeader

  }

  private def quorumLost:Receive = {

    case QuorumLost(`chainId`) =>
      leaderVotes = Seq.empty
      context become waitForQuorum

    case NotQuorumCandidate(`chainId`, `thisNodeId`) =>
      leaderVotes = Seq.empty
      context become waitForQuorum
  }


  private def findLeader(connectedMembers: Set[UniqueNodeIdentifier]): Receive =  quorumLost orElse {

    case FindTheLeader =>
      val findMsg = createFindLeader()
      log.info("Sending FindLeader to network ")
      //TODO PARAM TIMEOUT
      context.setReceiveTimeout(10 seconds)
      send(MessageKeys.FindLeader, findMsg, connectedMembers)

    case ReceiveTimeout => self ! FindTheLeader

    case IncomingMessage(`chainId`, MessageKeys.FindLeader, qMember,
              FindLeader(hisBlkHeight, hisCommittedTxIndex, hisSigIndx, hisId)) =>

      log.info("Someone asked us to vote on a leader (FindLeader)")
      val FindLeader(myBlockHeight, myCommittedTxIndex, mySigIndex, nodeId) = createFindLeader()

      if (hisBlkHeight > myBlockHeight) {
        // I vote for him
        log.info(s"My name is $nodeId and I'm voting for $hisId")
        send(MessageKeys.VoteLeader, VoteLeader(nodeId, myBlockHeight, myCommittedTxIndex), Set(qMember))

      } else if ((hisBlkHeight == myBlockHeight) && (hisCommittedTxIndex > myCommittedTxIndex)) {
        // I vote for him
        log.info(s"My name is $nodeId and I'm voting for $hisId")
        send(MessageKeys.VoteLeader, VoteLeader(nodeId,myBlockHeight, myCommittedTxIndex), Set(qMember))

      } else if ((hisBlkHeight == myBlockHeight) &&
              (hisCommittedTxIndex == myCommittedTxIndex) &&
              (mySigIndex > hisSigIndx)) {
        // I vote for him
        log.info(s"My name is $nodeId and I'm voting for $hisId")

        send(MessageKeys.VoteLeader,
          VoteLeader(nodeId, myBlockHeight, myCommittedTxIndex), Set(qMember))

      } else  if ((hisBlkHeight == myBlockHeight) &&
              (hisCommittedTxIndex == myCommittedTxIndex) &&
              (mySigIndex == hisSigIndx)) {
        // This can only happen when there are no txs in the chain. Very special case.
        // the sigs Must have an order. They can't be the same unless there are none.
        def makeLong(str: String) =
          str.foldLeft(0l)((acc, e) => acc + e.toLong)

        if (makeLong(nodeId) > makeLong(hisId)) {
          log.info(
            s"My name is $nodeId and I'm voting for $hisId in order to get started up.")
          send(MessageKeys.VoteLeader,
            VoteLeader(nodeId, myBlockHeight, myCommittedTxIndex), Set(qMember))
        }
      }


    case IncomingMessage(`chainId`, MessageKeys.VoteLeader, qMember, vl:VoteLeader) =>

      val oldSize = leaderVotes.size
      val l = leaderVotes.filterNot(_.nodeId == vl.nodeId)
      leaderVotes =  vl +: l

      if(leaderVotes.size > oldSize) {
        log.info(
          s"$thisNodeId got a vote from ${vl.nodeId}, now have ${leaderVotes.size} of $connectedMembers")
        if (leaderVotes.size == connectedMembers.size) {
          // I am the leader.
          context.setReceiveTimeout(Duration.Undefined)
          context.become(handle(thisNodeId, connectedMembers))

          send(MessageKeys.Leader, Leader(thisNodeId), connectedMembers)

          val useToGetBlockIndexDetails = createFindLeader()

          messageBus publish Synchronized(chainId,
            useToGetBlockIndexDetails.height,
            useToGetBlockIndexDetails.commitedTxIndex)

          messageBus publish WeAreLeader(chainId,
            useToGetBlockIndexDetails.height,
            useToGetBlockIndexDetails.commitedTxIndex,
            leaderVotes)

        }
      } else log.info(s"Strangely $thisNodeId got a duplicate vote from ${vl.nodeId}")


    case IncomingMessage(`chainId`, MessageKeys.Leader, leader, _) =>

      context.setReceiveTimeout(Duration.Undefined)
      context.become(handle(leader, connectedMembers))
      log.info(s"The leader is ${leader}")
      messageBus publish LeaderFound(`chainId`, leader, connectedMembers)

  }

  private def handle(leader: UniqueNodeIdentifier, members: Set[UniqueNodeIdentifier]): Receive = quorumLost orElse {

    case ConnectionLost(`leader`) =>
      context become findLeader(members - leader)
      self ! FindTheLeader

    case IncomingMessage(`chainId`, MessageKeys.FindLeader, from, _) =>
      if (leader == thisNodeId)
        send(MessageKeys.Leader, Leader(thisNodeId), Set(from))

    case IncomingMessage(`chainId`, MessageKeys.VoteLeader, from , _) =>
      log.info(
        s"Got an surplus vote from ${from}, leader is $leader")

    case IncomingMessage(`chainId`, MessageKeys.Leader, from, _) =>
      log.info(
        s"Got an unneeded leader indicator ${from}, leader is $leader")


  }
}
