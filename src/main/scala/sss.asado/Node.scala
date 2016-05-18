package sss.asado

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import akka.routing.RoundRobinPool
import sss.ancillary.{Configure, DynConfig, InitServlet, ServerLauncher}
import sss.asado.account.NodeIdentity
import sss.asado.block._
import sss.asado.console.ConsoleServlet
import sss.asado.http.TxServlet
import sss.asado.network.NetworkController.{BindControllerSettings, StartNetwork}
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

  case class InitWithActorRefs(refs : ActorRef *)

  def main(args: Array[String]) {

    println(s"Asado node starting up ...[${args(0)}]")

    val nodeConfig = config(args(0))
    val dbConfig = nodeConfig.getConfig("database")
    implicit val db = Db(dbConfig)

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](nodeConfig.getConfig("bind"))
    val nodeIdentity = NodeIdentity(nodeConfig)

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


    val chainDownloaderRef = actorSystem.actorOf(Props(classOf[BlockChainDownloaderActor], nodeIdentity, ncRef, messageRouter, bc, db))

    leaderActorRef ! InitWithActorRefs(ncRef, stateMachine)

    val blockChainSyncerActor = actorSystem.actorOf(Props(classOf[BlockChainSynchronizationActor],
      quorum,
      blockChainSettings.maxTxPerBlock,
      blockChainSettings.maxSignatures,
      stateMachine,
      bc,
      messageRouter,
      db))

    val txRouter: ActorRef = actorSystem.actorOf(RoundRobinPool(5).props(Props(classOf[TxWriter], blockChainSyncerActor)), "txRouter")
    val blockChainActor = actorSystem.actorOf(Props(classOf[BlockChainActor], nodeIdentity, blockChainSettings, bc, txRouter, blockChainSyncerActor, db))

    val txForwarder = actorSystem.actorOf(Props(classOf[TxForwarderActor],
      nodeIdentity.id,
      connectedPeers,
      messageRouter,
      nodeConfig.getInt("clientRefCacheSize")))

    blockChainSyncerActor ! InitWithActorRefs(blockChainActor)

    stateMachine ! InitWithActorRefs(chainDownloaderRef, leaderActorRef, messageRouter, txRouter, blockChainSyncerActor, blockChainActor, txForwarder)

    ncRef ! StartNetwork

    if(quorum == 0 && !nodeConfig.getBoolean("production")) stateMachine ! AcceptTransactions(settings.nodeId)

    val console = new ConsoleServlet(args, messageRouter, ncRef, connectedPeers, actorSystem, ncRef, bc, db)
    val txServlet = new TxServlet(args, messageRouter, ncRef, connectedPeers, actorSystem, ncRef, bc, db)

    val webServer = ServerLauncher(nodeConfig.getInt("consoleport"),
      InitServlet(console, "/console/*"),
      InitServlet(txServlet, "/tx/*"))

    webServer.start
    println(s"... node ${args(0)} started.")

    sys addShutdownHook( webServer stop )

  }

}
