package sss.asado

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import akka.routing.RoundRobinPool
import sss.ancillary.{Configure, DynConfig}
import sss.asado.block._
import sss.asado.console.ConsoleActor
import sss.asado.ledger.UTXOLedger
import sss.asado.network.MessageRouter.RegisterRef
import sss.asado.network.NetworkController.BindControllerSettings
import sss.asado.network._
import sss.asado.state.{AsadoStateMachineActor, LeaderActor}
import sss.asado.storage.UTXODBStorage
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

    db.table("utxo").map { println(_) }

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](nodeConfig.getConfig("bind"))

    implicit val actorSystem = ActorSystem("asado-network-node")
    val connectedPeers = Agent[Set[Connection]](Set.empty[Connection])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val blockChainSettings = DynConfig[BlockChainSettings](s"${args(0)}.blockchain")
    val bc = new BlockChain()
    val utxoLedger = new UTXOLedger(new UTXODBStorage())

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val peersList = nodeConfig.getStringList("peers").toSet.map(NetworkController.toInetSocketAddress)
    val quorum = NetworkController.quorum(peersList.size)

    val leaderActorRef = actorSystem.actorOf(Props(classOf[LeaderActor], settings.nodeId, quorum, connectedPeers, messageRouter, bc, db))

    val stateMachine = actorSystem.actorOf(Props(classOf[AsadoStateMachineActor],
       settings.nodeId, connectedPeers, bc))

    val netInf = new NetworkInterface(settings, uPnp)

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, netInf, peersList, connectedPeers, stateMachine))
    val chainDownloaderRef = actorSystem.actorOf(Props(classOf[BlockChainDownloaderActor], utxoLedger, ncRef, messageRouter, bc, db))

    stateMachine ! InitWithActorRefs(chainDownloaderRef, leaderActorRef)
    leaderActorRef ! InitWithActorRefs(ncRef, stateMachine)

    val blockChainSyncerActor = actorSystem.actorOf(Props(classOf[BlockChainSynchronizationActor], quorum, stateMachine, messageRouter, db))

    val txRouter: ActorRef =
      actorSystem.actorOf(RoundRobinPool(5).props(Props(classOf[TxWriter], blockChainSyncerActor)), "txRouter")

    messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)

    //val bcRef = actorSystem.actorOf(Props(classOf[BlockChainActor], blockChainSettings, bc, utxoLedger, txRouter, db))


    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, connectedPeers, db))

    ref ! "init"

  }

}
