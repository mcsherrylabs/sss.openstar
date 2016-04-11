package sss.asado

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import akka.routing.RoundRobinPool
import sss.ancillary.{Configure, DynConfig}
import sss.asado.block._
import sss.asado.console.ConsoleActor
import sss.asado.network.NetworkController.BindControllerSettings
import sss.asado.network._
import sss.asado.state.AsadoStateProtocol.AcceptTransactions
import sss.asado.state.{AsadoStateMachineActor, LeaderActor}
import sss.db.Db

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object Node extends Configure {


  /*trait ActorRefMarker {
    val ref : ActorRef
  }
  case class NetworkControllerRef(ref : ActorRef) extends ActorRefMarker*/

  case class InitWithActorRefs(refs : ActorRef *)

  def main(args: Array[String]) {

    println(s"Asado node starting up ...[${args(0)}]")

    val nodeConfig = config(args(0))
    val dbConfig = nodeConfig.getConfig("database")
    implicit val db = Db(dbConfig)

    db.table("utxo").map { println(_) }

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](nodeConfig.getConfig("bind"))

    implicit val actorSystem = ActorSystem("asado-network-node")
    val connectedPeers = Agent[Set[Connection]](Set.empty[Connection])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val blockChainSettings = DynConfig[BlockChainSettings](s"${args(0)}.blockchain")
    val bc = new BlockChainImpl()

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val peersList = nodeConfig.getStringList("peers").toSet.map(NetworkController.toNodeId)
    val quorum = NetworkController.quorum(peersList.size)

    val leaderActorRef = actorSystem.actorOf(Props(classOf[LeaderActor], settings.nodeId, quorum, connectedPeers, messageRouter, bc, db))

    val stateMachine = actorSystem.actorOf(Props(classOf[AsadoStateMachineActor],
       settings.nodeId, connectedPeers, blockChainSettings, bc, quorum, db))

    val netInf = new NetworkInterface(settings, uPnp)

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, netInf, peersList, connectedPeers, stateMachine))

    val chainDownloaderRef = actorSystem.actorOf(Props(classOf[BlockChainDownloaderActor], ncRef, messageRouter, bc, db))

    leaderActorRef ! InitWithActorRefs(ncRef, stateMachine)

    val blockChainSyncerActor = actorSystem.actorOf(Props(classOf[BlockChainSynchronizationActor], quorum, stateMachine, bc, messageRouter, db))

    val txRouter: ActorRef = actorSystem.actorOf(RoundRobinPool(5).props(Props(classOf[TxWriter], blockChainSyncerActor)), "txRouter")

    stateMachine ! InitWithActorRefs(chainDownloaderRef, leaderActorRef, messageRouter, txRouter, blockChainSyncerActor)

    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, connectedPeers, db))
    ncRef ! InitWithActorRefs()

    if(quorum == 0 && !nodeConfig.getBoolean("production")) stateMachine ! AcceptTransactions(settings.nodeId)

    ref ! "init"

  }

}
