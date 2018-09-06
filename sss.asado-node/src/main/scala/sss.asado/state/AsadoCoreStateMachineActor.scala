package sss.asado.state

import akka.actor.ActorRef
import sss.asado.block._
import sss.asado.network.{MessageEventBus, NetworkRef}
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
                                 blockChainSettings: BlockChainSettings,
                                 bc: BlockChain,
                                 messageRouter: MessageEventBus,
                                 ncRef:NetworkRef,
                                 db: Db
                             ) extends AsadoStateMachine {

  log.info("AsadoCoreStateMachine actor has started...")

  final override def receive = init orElse super.receive


  private def init: Receive = {
    case InitWithActorRefs(
                              leaderRef,
                              txRouter,
                              blockChainActor,
                              txForwarder) =>
      log.info("AsadoCoreStateMachine actor has been initialized...")
      context.become(stateTransitionTasks(
        leaderRef,
        messageRouter,
        txRouter,
        blockChainActor,
        txForwarder) orElse super.receive)

  }

  def stateTransitionTasks(
                            leaderRef: ActorRef,
                            messageRouter: MessageEventBus,
                            txRouter: ActorRef,
                            blockChainActor: ActorRef,
                            txForwarder: ActorRef): Receive = {


    case  swl @ SplitRemoteLocalLeader(leader) =>
      if(thisNodeId == leader) {
        log.info(s"We are leader - $leader, we will respond to syncing requests ... ")
        publish(LocalLeaderEvent)
      } else {
        log.info(s"New leader is $leader, begin syncing ... ")
        ncRef.connections().find(_.nodeId == leader) match {
          case None => log.warning(s"Could not find leader $leader in peer connections!")
          case Some(c) => publish(RemoteLeaderEvent(c))
        }
      }

    case BlockChainStarted(BlockChainUp) =>
      messageRouter.subscribe(MessageKeys.SignedTx)( txRouter)
      messageRouter.subscribe(MessageKeys.SeqSignedTx)( txRouter)

    case BlockChainStopped(BlockChainDown) => log.info("Block chain has stopped.")

    case CommandFailed(BlockChainUp) => context.system.scheduler.scheduleOnce(2 seconds, blockChainActor , StartBlockChain(self, BlockChainUp))

    case AcceptTransactions(leader) =>
      if(thisNodeId == leader) {
        log.info("Tx Accept :D")
        blockChainActor ! StartBlockChain(self, BlockChainUp)
      } else log.info("Begin Tx forward...")

    case StopAcceptingTransactions =>
      messageRouter.unsubscribe(MessageKeys.SignedTx)(txRouter)
      messageRouter.unsubscribe(MessageKeys.SeqSignedTx)(txRouter)
      blockChainActor ! StopBlockChain(self, BlockChainDown)
      log.info("Stop Tx Accept!!")

  }
}
