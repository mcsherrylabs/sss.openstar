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

  final override def receive = init orElse super.receive

  private def init: Receive = {
    case InitWithActorRefs(
                              chainDownloaderRef,
                              leaderRef,
                              messageRouter,
                              txRouter,
                              blockChainActor,
                              txForwarder) =>
      log.info("AsadoCoreStateMachine actor has been initialized...")
      context.become(stateTransitionTasks(chainDownloaderRef,
        leaderRef,
        messageRouter,
        txRouter,
        blockChainActor,
        txForwarder) orElse super.receive)

  }


  def stateTransitionTasks(chainDownloaderRef: ActorRef,
                           leaderRef: ActorRef,
                           messageRouter: ActorRef,
                           txRouter: ActorRef,
                           blockChainActor: ActorRef,
                           txForwarder: ActorRef): Receive = {

    case  FindTheLeader =>
      log.info("We need to find the leader ...")
      leaderRef ! FindTheLeader
      txForwarder ! StopAcceptingTransactions

    case  swl @ SyncWithLeader(leader) =>
      if(thisNodeId == leader) {
        log.info(s"We are leader - $leader, we will respond to syncing requests ... ")
      } else {
        log.info(s"Leader is $leader, begin syncing ... ")
        connectedPeers().find(_.nodeId.id == leader) match {
          case None => log.warning(s"Could not find leader $leader in peer connections!")
          case Some(c) =>
            chainDownloaderRef ! SynchroniseWith(c)
            txForwarder ! Forward(c)
        }
      }

    case BlockChainStarted(BlockChainUp) => messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)
    case BlockChainStopped(BlockChainDown) => log.info("Block chain has stopped.")
    case CommandFailed(BlockChainUp) => context.system.scheduler.scheduleOnce(2 seconds, blockChainActor , StartBlockChain(self, BlockChainUp))

    case AcceptTransactions(leader) =>
      log.info("Tx Accept :D")
      if(thisNodeId == leader) {
        blockChainActor ! StartBlockChain(self, BlockChainUp)
      }

    case  StopAcceptingTransactions =>
      messageRouter ! UnRegisterRef(MessageKeys.SignedTx, txRouter)
      blockChainActor ! StopBlockChain(self, BlockChainDown)
      log.info("Stop Tx Accept!!")


    case Connecting => log.info("Connecting!!")
  }
}
