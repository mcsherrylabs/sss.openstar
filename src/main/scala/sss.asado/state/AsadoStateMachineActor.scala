package sss.asado.state

import akka.actor.ActorRef
import akka.agent.Agent
import sss.asado.MessageKeys
import sss.asado.Node.InitWithActorRefs
import sss.asado.block._
import sss.asado.network.Connection
import sss.asado.network.MessageRouter.{RegisterRef, UnRegisterRef}
import sss.asado.state.AsadoStateProtocol._
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps


/**
  * Created by alan on 4/1/16.
  */
class AsadoStateMachineActor(thisNodeId: String,
                             connectedPeers: Agent[Set[Connection]],
                             blockChainSettings: BlockChainSettings,
                             bc: BlockChain,
                             quorum: Int,
                             db: Db
                             ) extends AsadoStateMachine {

  final override def receive = init orElse super.receive

  private def init: Receive = {
    case InitWithActorRefs(chainDownloaderRef,
                              leaderRef,
                              messageRouter,
                              txRouter,
                              blockChainSyncerActor,
                              blockChainActor,
                              txForwarder) =>

      context.become(stateTransitionTasks(chainDownloaderRef,
        leaderRef,
        messageRouter,
        txRouter,
        blockChainSyncerActor,
        blockChainActor,
        txForwarder) orElse super.receive)

  }


  def stateTransitionTasks(chainDownloaderRef: ActorRef,
                           leaderRef: ActorRef,
                           messageRouter: ActorRef,
                           txRouter: ActorRef,
                           blockChainSyncerActor: ActorRef,
                           blockChainActor: ActorRef,
                           txForwarder: ActorRef): Receive = {

    case  FindTheLeader =>
      log.info("We need to find the leader ...")
      leaderRef ! FindTheLeader
      txForwarder ! StopAcceptingTransactions

    case  swl @ SyncWithLeader(leader) =>
      if(thisNodeId == leader) {
        log.info(s"We are leader - $leader, we will repsond to syncing ... ")
      } else {
        log.info(s"Leader is $leader, begin syncing ... ")
        chainDownloaderRef ! swl
        txForwarder ! Forward(leader)
      }

    case BlockChainStarted(BlockChainUp) => messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)
    case BlockChainStopped(BlockChainDown) => log.info("Block chain has stopped.")

    case  CommandFailed(BlockChainUp) => context.system.scheduler.scheduleOnce(1 seconds, blockChainActor , StartBlockChain(self, BlockChainUp))
    case  AcceptTransactions(leader) =>
      log.info("Tx Accept :D")
      if(thisNodeId == leader) {
        blockChainActor ! StartBlockChain(self, BlockChainUp)
        messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)
      }

    case  StopAcceptingTransactions =>
      messageRouter ! UnRegisterRef(MessageKeys.SignedTx, txRouter)
      blockChainActor ! StopBlockChain(self, BlockChainDown)
      log.info("Stop Tx Accept!!")


    case Connecting => log.info("Connecting!!")
  }
}
