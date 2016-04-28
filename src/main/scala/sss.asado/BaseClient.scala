package sss.asado

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import sss.ancillary.{Configure, DynConfig, Logging}
import sss.asado.network.NetworkController.{BindControllerSettings, StartNetwork}
import sss.asado.network._
import sss.db.Db

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
case class ClientContext(  args: Array[String],
                           actorSystem: ActorSystem,
                           connectPeers: Agent[Set[Connection]],
                           peers: Set[NodeId],
                           messageRouter: ActorRef,
                           networkController: ActorRef,
                           nodeConfig: Config,
                          implicit val db: Db)

trait BaseClient extends Configure with Logging {


  def main(args: Array[String]) {

    println(s"Asado client starting up ...[${args(0)}]")

    val nodeConfig = config(args(0))
    val dbConfig = s"${args(0)}.database"
    implicit val db = Db(dbConfig)

    val settings: BindControllerSettings = DynConfig[BindControllerSettings](s"${args(0)}.bind")

    implicit val actorSystem = ActorSystem("asado-network-client")
    val connectedPeers = Agent[Set[Connection]](Set.empty[Connection])

    val peersList = nodeConfig.getStringList("peers").toSet.map(NetworkController.toNodeId)

    val messageRouter = actorSystem.actorOf(Props(classOf[MessageRouter]))

    val uPnp = DynConfig.opt[UPnPSettings](s"${args(0)}.upnp") map (new UPnP(_))

    val netInf = new NetworkInterface(settings, uPnp)

    val stateMachine = createStateMachine(actorSystem, messageRouter, peersList)

    val ncRef = actorSystem.actorOf(Props(classOf[NetworkController], messageRouter, netInf, peersList, connectedPeers, stateMachine))

    ncRef ! StartNetwork

    Try {
      run(ClientContext(args, actorSystem, connectedPeers, peersList, messageRouter, networkController = ncRef, nodeConfig, db))
    } match {
      case Success(_) => log.info("client terminating normally")
      case Failure(e) => log.error("Client terminating! ", e)
    }
    actorSystem.terminate()

  }

  protected def createStateMachine(actorSystem: ActorSystem,
    messageRouterRef: ActorRef,
    peers: Set[NodeId]): ActorRef =  actorSystem.actorOf(Props[DumbMachine])

  protected def run(context: ClientContext)
}

class DumbMachine extends Actor with ActorLogging {
  override def receive: Receive = {
    case x => log.info(x.toString)
  }
}