package sss.analysis

import akka.actor.{ActorSystem, Props}
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.ClientNode
import sss.ui.reactor.ReactorActorSystem

/**
  * Created by alan on 6/10/16.
  */
object Main {

  var server :ServerLauncher = _
  var clientNode: ClientNode = _

  def main(args: Array[String]) {

    clientNode = new ClientNode {
      override val phrase: Option[String] = Some("password")
      override val configName: String = "analysis"
      lazy override val actorSystem: ActorSystem = ReactorActorSystem.actorSystem
    }

    clientNode.actorSystem.actorOf(Props(classOf[AnalysingActor], clientNode))
    clientNode.initStateMachine


    val httpConfig = DynConfig[ServerConfig]("httpServerConfig")
    server = ServerLauncher(httpConfig,
      ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
      ServletContext("/service", ""))

    server.start
    server.join
  }

  def buildUIServlet = new sss.ui.Servlet

}

