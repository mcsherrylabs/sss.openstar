package sss.asado

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import sss.ancillary.{Configure, DynConfig}
import sss.asado.console.{ConsoleActor, InfoActor, NoRead}
import sss.asado.ledger.Ledger
import sss.asado.network.MessageRouter.Register
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

    println("Asado node starting up ...")

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-node")
    val peerList = Agent[Set[ConnectedPeer]](Set.empty[ConnectedPeer])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = if(config.hasPath(s"${args(0)}.upnp")) Option(new UPnP(DynConfig[UPnPSettings](s"${args(0)}.upnp")))
    else None

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, settings, uPnp, peerList))

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
