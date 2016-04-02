package sss.asado

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import sss.ancillary.{Configure, DynConfig}
import sss.asado.console.ConsolePattern
import sss.asado.network.NetworkController.{BindControllerSettings, ConnectTo}
import sss.asado.network._
import sss.db.Db

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
trait BaseClient extends Configure with ConsolePattern {


  def main(args: Array[String]) {

    println(s"Asado client starting up ...[${args(0)}]")

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"
    implicit val db = Db(dbConfig)

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-client")
    val peerList = Agent[Set[Connection]](Set.empty[Connection])

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], false, messageRouter, settings, uPnp, peerList))

    val peers: scala.collection.mutable.Seq[String] = nodeConfig.getStringList("peers")
    peers.foreach { case peerPattern(ip, port) =>
      //actorSystem.scheduler.schedule(0 second, 30 seconds, ncRef, ConnectTo(new InetSocketAddress(ip, port.toInt)))
      ncRef !  ConnectTo(NodeId("some place", new InetSocketAddress(ip, port.toInt)))
    }

    run(settings, actorSystem, peerList, messageRouter, ncRef, nodeConfig, args)

  }

  protected def run(settings: BindControllerSettings,
                    actorSystem: ActorSystem,
                    peerList :Agent[Set[Connection]],
                    messageRouter: ActorRef,
                    ncRef: ActorRef,
                    nodeConfig: Config,
                    args: Array[String]
                 )(implicit db: Db)
}
