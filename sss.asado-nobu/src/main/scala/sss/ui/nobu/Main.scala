package sss.ui.nobu

import akka.actor.{ActorRef, ActorSystem, Props}
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.ClientNode
import sss.ui.reactor.ReactorActorSystem

/**
  * Created by alan on 6/10/16.
  */
object Main {

  var server: ServerLauncher = _
  var clientNode: ClientNode = _
  var clientEventActor: ActorRef = _

  def main(args: Array[String]) {

    clientNode = new ClientNode {
      override val phrase: Option[String] = Some("password")
      override val configName: String = "node"
      lazy override val actorSystem: ActorSystem = ReactorActorSystem.actorSystem
    }

    clientEventActor = clientNode.actorSystem.actorOf(Props(classOf[ClientEventActor], clientNode))
    clientNode.initStateMachine

    val httpConfig = DynConfig[ServerConfig]("httpServerConfig")
    server = ServerLauncher(httpConfig,
      ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
      ServletContext("/console", "", InitServlet(clientNode.buildConsoleServlet.get, "/*")),
      ServletContext("/service", ""))

    server.start
    server.join
  }

  def buildUIServlet = new sss.ui.Servlet

}

