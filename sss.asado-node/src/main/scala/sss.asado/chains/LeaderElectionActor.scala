package sss.asado.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import sss.asado.block._
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor._
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.{MessageEventBus, _}
import sss.asado._
import sss.asado.common.block.BlockId
import math.Ordered.orderingToOrdered
import sss.db.Db

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by alan on 4/1/16.
  */
object LeaderElectionActor {

  type MakeFindLeader = () => FindLeader


  trait LeaderFound extends AsadoEvent {
    val chainId: GlobalChainIdMask
    val leader: UniqueNodeIdentifier
  }

  case class LocalLeader(chainId: GlobalChainIdMask,
                         leader: UniqueNodeIdentifier,
                         height: Long,
                         index:Long,
                         followers: Seq[VoteLeader]) extends LeaderFound

  case class RemoteLeader(chainId: GlobalChainIdMask,
                          leader: String,
                          members: Set[UniqueNodeIdentifier]) extends LeaderFound

  case class LeaderLost(chainId: GlobalChainIdMask,
                         leader: String)  extends AsadoEvent

  private case object FindTheLeader

  private def createFindLeaderMsg(thisNodeId: UniqueNodeIdentifier,
                                   bc: BlockChain with BlockChainSignaturesAccessor)(implicit db: Db): FindLeader = {

    val recordedId = bc.getLatestRecordedBlockId()
    val committedId = bc.getLatestCommittedBlockId()

    assert(recordedId.blockHeight == committedId.blockHeight,
      s"Cannot have an uncommitted tx ${recordedId} in a different block (committed${committedId})")


    val sigIndex = bc.quorumSigs(recordedId.blockHeight).indexOfBlockSignature(thisNodeId)
      .getOrElse(Int.MaxValue)

    FindLeader(recordedId.blockHeight,
      recordedId.txIndex,
      committedId.txIndex,
      sigIndex,
      thisNodeId)
  }

  def apply(thisNodeId: UniqueNodeIdentifier,
            createFindLeader: MakeFindLeader,
            getLatestCommittedBlockId: GetLatestCommittedBlockId)
           (implicit  chainId: GlobalChainIdMask,
            messageBus: MessageEventBus,
            send: Send,
            actorSystem: ActorSystem): ActorRef = {

    actorSystem.actorOf(Props(classOf[LeaderElectionActor],
      thisNodeId,
      messageBus,
      send,
      createFindLeader,
      getLatestCommittedBlockId,
      chainId), s"LeaderElectionActor_$chainId")
  }


    def apply(thisNodeId: UniqueNodeIdentifier,
            bc: BlockChain with BlockChainSignaturesAccessor)
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageBus: MessageEventBus,
            actorSystem: ActorSystem): ActorRef = {

    def createFindLeader: MakeFindLeader = () => createFindLeaderMsg(thisNodeId, bc)


    apply(thisNodeId, createFindLeader, () => bc.getLatestCommittedBlockId())
  }
}

private class LeaderElectionActor(
                                   thisNodeId: UniqueNodeIdentifier,
                                   messageBus: MessageEventBus,
                                   send: Send,
                                   createFindLeader: MakeFindLeader,
                                   getLatestCommittedBlockId: GetLatestCommittedBlockId)(implicit chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging {

  messageBus.subscribe(classOf[Quorum])

  log.info("Leader actor has started...")

  private var connectedMembers: Set[UniqueNodeIdentifier] = Set()

  private var leaderVotes: Seq[VoteLeader] = Seq.empty

  def receive = waitForQuorum

  private def waitForQuorum: Receive = {

    case Quorum(`chainId`, connected, _) =>

      connectedMembers = connected
      messageBus.subscribe(classOf[QuorumLost])
      messageBus.subscribe(classOf[ConnectionLost])
      messageBus.subscribe(MessageKeys.FindLeader)
      messageBus.subscribe(MessageKeys.Leader)
      messageBus.subscribe(MessageKeys.VoteLeader)

      if(connectedMembers.isEmpty) {
        //Special case where there is a quorum and it's just us.
        context.become(handle(thisNodeId))
        publishLeaderEvents(getLatestCommittedBlockId())
      } else {
        context become findLeader
        self ! FindTheLeader
      }
  }

  private def quorumLostNoLeader:Receive = {

    case QuorumLost(`chainId`) =>
      leaderVotes = Seq.empty
      messageBus.unsubscribe(classOf[QuorumLost])
      messageBus.unsubscribe(classOf[ConnectionLost])
      messageBus.unsubscribe(MessageKeys.FindLeader)
      messageBus.unsubscribe(MessageKeys.Leader)
      messageBus.unsubscribe(MessageKeys.VoteLeader)

      context become waitForQuorum
  }

  private def updateQuorum: Receive = {
    case Quorum(`chainId`, connected, _) =>
      connectedMembers = connected
  }

  private def quorumLost(leader: UniqueNodeIdentifier):Receive = {

    case QuorumLost(`chainId`) =>
      leaderVotes = Seq.empty
      context become waitForQuorum
      messageBus.publish(LeaderLost(chainId, leader))

  }


  private def findLeader: Receive = updateQuorum orElse quorumLostNoLeader orElse {

    case FindTheLeader =>
      val findMsg = createFindLeader()
      log.info(s"Sending FindLeader to network ${findMsg}")
      //TODO PARAM TIMEOUT
      context.setReceiveTimeout(10 seconds)
      send(MessageKeys.FindLeader, findMsg, connectedMembers)

    case ReceiveTimeout => self ! FindTheLeader

    case IncomingMessage(`chainId`, MessageKeys.FindLeader, qMember,
              his@FindLeader(hisBlkHeight, hisRecordedTxIndex, hisCommittedTxIndex, hisSigIndx, hisId)) =>

      val ours@FindLeader(myBlockHeight, myRecordedTxIndex, myCommittedTxIndex, mySigIndex, nodeId) = createFindLeader()
      log.info("{} asked us to vote on a leader (FindLeader)", qMember)
      log.info("His  - {} ", his)
      log.info("Ours - {} ", ours)

      if(his > ours) {
        //TODO use hashes of (prevBlock,nodeId) and hash of (prevBlock,hisId) to rotate the leadership
        log.info(s"My name is $nodeId and I'm voting for $hisId")
        send(MessageKeys.VoteLeader, VoteLeader(nodeId, myBlockHeight, myCommittedTxIndex), Set(qMember))
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
          context.become(handle(thisNodeId))

          send(MessageKeys.Leader, Leader(thisNodeId), connectedMembers)

          val latestBlockId = getLatestCommittedBlockId()

          publishLeaderEvents(latestBlockId)

        }
      } else log.info(s"Strangely $thisNodeId got a duplicate vote from ${vl.nodeId}")


    case IncomingMessage(`chainId`, MessageKeys.Leader, leader, _) =>

      context.setReceiveTimeout(Duration.Undefined)
      context.become(handle(leader))
      log.info(s"The leader is ${leader}")
      messageBus publish RemoteLeader(`chainId`, leader, connectedMembers)

  }

  private def publishLeaderEvents(latestBlockId: BlockId) = {

    messageBus publish LocalLeader(chainId,
      thisNodeId,
      latestBlockId.blockHeight,
      latestBlockId.txIndex,
      leaderVotes)
  }

  private def handle(leader: UniqueNodeIdentifier): Receive = updateQuorum orElse quorumLost(leader) orElse {

    case ConnectionLost(`leader`) =>
      connectedMembers -= leader
      context become findLeader
      self ! FindTheLeader
      messageBus.publish(LeaderLost(chainId, leader))

    case IncomingMessage(`chainId`, MessageKeys.FindLeader, from, _) =>
      if (leader == thisNodeId)
        send(MessageKeys.Leader, Leader(thisNodeId), Set(from))

    case IncomingMessage(`chainId`, MessageKeys.VoteLeader, from , _) =>
      log.info(
        s"Got an surplus vote from ${from}, leader is $leader")

    case IncomingMessage(`chainId`, MessageKeys.Leader, from, saying) =>
      log.info(
        s"Got an unneeded leader indicator from ${from}, saying $saying but leader is $leader")


  }
}
