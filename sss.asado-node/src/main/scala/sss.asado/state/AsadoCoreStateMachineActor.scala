package sss.asado.state

import akka.actor.ActorRef
import akka.agent.Agent
import sss.asado.block._
import sss.asado.network.Connection
import sss.asado.network.MessageRouter.{RegisterRef, UnRegisterRef}
import sss.asado.state.AsadoStateProtocol._
import sss.asado.{InitWithActorRefs, MessageKeys}
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps


/**
  * Created by alan on 4/1/16.
  */

class AsadoCoreStateMachineActor(thisNodeId: String,
                                 connectedPeers: Agent[Set[Connection]],
                                 blockChainSettings: BlockChainSettings,
                                 bc: BlockChain,
                                 quorum: Int,
                                 db: Db
                             ) extends AsadoStateMachine {

  log.info("AsadoCoreStateMachine actor has started...")

  final override def receive = init orElse eventRegistration orElse super.receive

  private var registerRefs: Set[ActorRef] = Set.empty

  private def eventRegistration: Receive = {

    case RegisterStateEvents => registerRefs += sender()
    case DeRegisterStateEvents => registerRefs -= sender()
    case Propagate(ev) => registerRefs foreach (_ ! ev)
  }

  private def init: Receive = {
    case InitWithActorRefs(
                              leaderRef,
                              messageRouter,
                              txRouter,
                              blockChainActor,
                              txForwarder) =>
      log.info("AsadoCoreStateMachine actor has been initialized...")
      context.become(stateTransitionTasks(
        leaderRef,
        messageRouter,
        txRouter,
        blockChainActor,
        txForwarder) orElse eventRegistration orElse super.receive)

  }

  def stateTransitionTasks(
                           leaderRef: ActorRef,
                           messageRouter: ActorRef,
                           txRouter: ActorRef,
                           blockChainActor: ActorRef,
                           txForwarder: ActorRef): Receive = {


    case  swl @ SplitRemoteLocalLeader(leader) =>
      if(thisNodeId == leader) {
        log.info(s"We are leader - $leader, we will respond to syncing requests ... ")
        self ! Propagate(LocalLeaderEvent)
      } else {
        log.info(s"New leader is $leader, begin syncing ... ")
        connectedPeers().find(_.nodeId.id == leader) match {
          case None => log.warning(s"Could not find leader $leader in peer connections!")
          case Some(c) => self ! Propagate(RemoteLeaderEvent(c))
        }
      }

    case BlockChainStarted(BlockChainUp) =>
      messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)
      messageRouter ! RegisterRef(MessageKeys.SeqSignedTx, txRouter)

    case BlockChainStopped(BlockChainDown) => log.info("Block chain has stopped.")

    case CommandFailed(BlockChainUp) => context.system.scheduler.scheduleOnce(2 seconds, blockChainActor , StartBlockChain(self, BlockChainUp))

    case AcceptTransactions(leader) =>
      if(thisNodeId == leader) {
        log.info("Tx Accept :D")
        blockChainActor ! StartBlockChain(self, BlockChainUp)
      } else log.info("Begin Tx forward...")

    case StopAcceptingTransactions =>
      messageRouter ! UnRegisterRef(MessageKeys.SignedTx, txRouter)
      messageRouter ! UnRegisterRef(MessageKeys.SeqSignedTx, txRouter)
      blockChainActor ! StopBlockChain(self, BlockChainDown)
      log.info("Stop Tx Accept!!")

  }
}
