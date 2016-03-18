package sss.asado

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import akka.routing.RoundRobinPool
import sss.ancillary.{Configure, DynConfig}
import sss.asado.block.TxWriter
import sss.asado.console.{ConsoleActor, InfoActor, NoRead}
import sss.asado.ledger.Ledger
import sss.asado.network.MessageRouter.{Register, RegisterRef}
import sss.asado.network._
import sss.asado.storage.DBStorage

import scala.collection.JavaConversions._
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

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-node")
    val peerList = Agent[Set[ConnectedPeer]](Set.empty[ConnectedPeer])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, settings, uPnp, peerList))

    val txRouter: ActorRef =
      actorSystem.actorOf(RoundRobinPool(5).props(Props[TxWriter]), "txRouter")

    messageRouter ! RegisterRef(MessageKeys.SignedTx, txRouter)

    //val bcRef = actorSystem.actorOf(Props(classOf[BlockChain], args(0), Seq(1,2), messageRouter))

    val ledger = new Ledger(new DBStorage(dbConfig))

    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, peerList, ledger))

    val infoRef = actorSystem.actorOf(Props(classOf[InfoActor], messageRouter, ledger ))

    infoRef ! Register(1)
    infoRef ! Register(2)

    val peers: scala.collection.mutable.Seq[String] = nodeConfig.getStringList("peers")
    peers.foreach(p => ref ! NoRead(s"connect $p"))

    ref ! "init"
  }

}
