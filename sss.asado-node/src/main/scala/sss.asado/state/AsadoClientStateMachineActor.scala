package sss.asado.state

import akka.actor.ActorRef
import akka.agent.Agent
import sss.asado.InitWithActorRefs
import sss.asado.block._
import sss.asado.message.CheckForMessages
import sss.asado.network.Connection
import sss.asado.state.AsadoStateProtocol._
import sss.db.Db

import scala.language.postfixOps


/**
  * Created by alan on 4/1/16.
  */
class AsadoClientStateMachineActor(thisNodeId: String,
                                   connectedPeers: Agent[Set[Connection]],
                                   blockChainSettings: BlockChainSettings,
                                   bc: BlockChain,
                                   quorum: Int,
                                   db: Db,
                                   protected val eventListener: ActorRef
                             ) extends AsadoClientStateMachine {


  final override def receive = init orElse super.receive

  private def init: Receive = {
    case InitWithActorRefs(messageDownloader,
                              chainDownloaderRef,
                              messageRouter,
                              txForwarder) =>
      log.info("AsadoClientStateMachine actor has been initialized...")
      context.become(stateTransitionTasks(
        messageDownloader,
        chainDownloaderRef,
        messageRouter,
        txForwarder) orElse super.receive)
      eventListener ! StateMachineInitialised
  }


  def stateTransitionTasks(messageDownloader: ActorRef,
                           chainDownloaderRef: ActorRef,
                           messageRouter: ActorRef,
                             txForwarder: ActorRef): Receive = {

    case  swl @ RemoteLeaderEvent(conn) =>
      eventListener ! swl
      chainDownloaderRef ! SynchroniseWith(conn)
      txForwarder ! Forward(conn)
      messageDownloader ! CheckForMessages

  }
}
