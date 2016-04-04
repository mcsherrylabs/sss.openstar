package sss.asado

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import sss.ancillary.{Configure, DynConfig}
import sss.asado.console.ConsolePattern
import sss.asado.network.NetworkController.BindControllerSettings
import sss.asado.network._

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
    //implicit val db = Db(dbConfig)

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-client")
    val connectedPeers = Agent[Set[Connection]](Set.empty[Connection])

    val peersList = nodeConfig.getStringList("peers").toSet.map(NetworkController.toNodeId)

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val netInf = new NetworkInterface(settings, uPnp)

    val stateMachine = actorSystem.actorOf(Props[DumbMachine])

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, netInf, peersList, connectedPeers, stateMachine))

    run(settings, actorSystem, connectedPeers, messageRouter, ncRef, nodeConfig, args)

  }

  protected def run(settings: BindControllerSettings,
                    actorSystem: ActorSystem,
                    connectedPeers :Agent[Set[Connection]],
                    messageRouter: ActorRef,
                    ncRef: ActorRef,
                    nodeConfig: Config,
                    args: Array[String])
}

class DumbMachine extends Actor with ActorLogging {
  override def receive: Receive = {
    case x => log.info(x.toString)
  }
}