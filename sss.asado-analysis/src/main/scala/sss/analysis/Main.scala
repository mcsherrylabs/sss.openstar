package sss.analysis

import akka.actor.{ActorSystem, Props}
import org.joda.time.format.DateTimeFormat
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.ClientNode
import sss.ui.ExportServlet
import sss.ui.reactor.ReactorActorSystem

/**
  * Created by alan on 6/10/16.
  */
object Main {

  var server :ServerLauncher = _
  var clientNode: ClientNode = _

  val dateFormat =  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  def main(args: Array[String]) {

    clientNode = new ClientNode {
      override val phrase: Option[String] = Some("password")
      override val configName: String = "analysis"
      lazy override val actorSystem: ActorSystem = ReactorActorSystem.actorSystem
    }

    clientNode.actorSystem.actorOf(Props(classOf[AnalysingActor], clientNode).withDispatcher("my-pinned-dispatcher"))
    clientNode.initStateMachine

    // wouldn't pure config lib be a cleaner solution (ie reduce code size)
    val httpConfig = DynConfig[ServerConfig]("httpServerConfig")
    server = ServerLauncher(httpConfig,
      ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
      ServletContext("/export", "", InitServlet(new ExportServlet(new TransactionHistoryPersistence()(clientNode.db)), "/*")))

    server.start
    server.join
  }

  def buildUIServlet = new sss.ui.Servlet

}

