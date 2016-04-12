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
    case InitWithActorRefs(chainDownloaderRef, leaderRef, messageRouter, txRouter, blockChainSyncerActor, blockChainActor) =>
      context.become(stateTransitionTasks(chainDownloaderRef, leaderRef, messageRouter, txRouter, blockChainSyncerActor, blockChainActor) orElse super.receive)
  }


  def stateTransitionTasks(chainDownloaderRef: ActorRef,
                           leaderRef: ActorRef,
                           messageRouter: ActorRef,
                           txRouter: ActorRef,
                           blockChainSyncerActor: ActorRef,
                           blockChainActor: ActorRef): Receive = {

    case  FindTheLeader =>
      log.info("We need to find the leader ...")
      leaderRef ! FindTheLeader

    case  swl @ SyncWithLeader(leader) =>
      if(thisNodeId == leader) {
        log.info(s"We are leader - $leader, we will repsond to syncing ... ")
      } else {
        log.info(s"Leader is $leader, begin syncing ... ")
        chainDownloaderRef ! swl
      }

    case BlockChainStarted(BlockChainUp) => messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)
    case BlockChainStopped(BlockChainDown) => log.info("Block chain has stopped.")

    case  AcceptTransactions(leader) =>
      log.info("Tx Accept :D")
      if(thisNodeId == leader) {
        blockChainActor ! StartBlockChain(self, BlockChainUp)
        messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)
      } else {
        // ignore for now. Eventually forward them.
      }

    case  StopAcceptingTransactions =>
      messageRouter ! UnRegisterRef(MessageKeys.SignedTx, txRouter)
      blockChainActor ! StopBlockChain(self, BlockChainDown)
      log.info("Stop Tx Accept!!")

    case Connecting => log.info("Connecting!!")
  }
}
