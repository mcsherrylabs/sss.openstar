package sss.asado

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import akka.routing.RoundRobinPool
import sss.ancillary.{Configure, DynConfig}
import sss.asado.block.{BlockChain, BlockChainActor, BlockChainSettings, TxWriter}
import sss.asado.console.ConsoleActor
import sss.asado.ledger.UTXOLedger
import sss.asado.network.MessageRouter.RegisterRef
import sss.asado.network._
import sss.asado.storage.UTXODBStorage
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object Node extends Configure {



  def main(args: Array[String]) {

    println(s"Asado node starting up ...[${args(0)}]")

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"
    implicit val db = Db(dbConfig)

    db.table("utxo").map { println(_) }

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-node")
    val peerList = Agent[Set[ConnectedPeer]](Set.empty[ConnectedPeer])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, settings, uPnp, peerList))

    val txRouter: ActorRef =
      actorSystem.actorOf(RoundRobinPool(5).props(Props[TxWriter]), "txRouter")

    messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)

    val blockChainSettings = DynConfig[BlockChainSettings](s"${args(0)}.blockchain")
    val bc = new BlockChain()

    val utxoLedger = new UTXOLedger(new UTXODBStorage())

    val bcRef = actorSystem.actorOf(Props(classOf[BlockChainActor], blockChainSettings, bc, utxoLedger, txRouter, db))

    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, peerList, db))

    ref ! "init"
  }

}
