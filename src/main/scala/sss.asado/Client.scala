package sss.asado

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.agent.Agent
import sss.ancillary.{Configure, DynConfig}
import sss.asado.console.{ConsoleActor, ConsolePattern, InfoActor}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.ConnectTo
import sss.asado.network._
import sss.db.Db

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object Client extends Configure with ConsolePattern {


  def main(args: Array[String]) {

    println(s"Asado client starting up ...[${args(0)}]")

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"
    implicit val db = Db(dbConfig)

    sys addShutdownHook( db shutdown)

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-client")
    val peerList = Agent[Set[ConnectedPeer]](Set.empty[ConnectedPeer])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, settings, uPnp, peerList))

    val ref = actorSystem.actorOf(Props(classOf[ConsoleActor], args, messageRouter, ncRef, peerList))

    val infoRef = actorSystem.actorOf(Props(classOf[InfoActor], messageRouter))

    infoRef ! Register(MessageKeys.SignedTxAck)
    infoRef ! Register(MessageKeys.SignedTxNack)

    val peers: scala.collection.mutable.Seq[String] = nodeConfig.getStringList("peers")
    peers.foreach { case peerPattern(ip, port) =>
      ncRef ! ConnectTo(InetSocketAddress.createUnresolved(ip, port.toInt))
    }

    ref ! "init"
  }

}
