package sss.analysis

import akka.actor.{ActorSystem, Props}
import com.vaadin.server.{UIClassSelectionEvent, UICreateEvent, UIProvider}
import com.vaadin.ui.UI
import org.joda.time.format.DateTimeFormat
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.PartialNode
import sss.asado.quorumledger.QuorumService
import sss.ui.{ExportServlet, MainUI}

import scala.util.Try

/**
  * Created by alan on 6/10/16.
  */
object Main {

  trait ClientNode extends PartialNode

  val dateFormat =  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  def main(args: Array[String]) {

    new ClientNode {

      clientNode: ClientNode =>

      override val phrase: Option[String] = Option("password")
      override val configName: String = "analysis"

      Try(QuorumService.create(globalChainId, "bob", "alice", "eve"))

      init // <- init delayed until phrase can be initialised.

      clientNode.actorSystem.actorOf(Props(classOf[AnalysingActor], clientNode).withDispatcher("my-pinned-dispatcher"))

      val httpConfig = DynConfig[ServerConfig]("httpServerConfig")

      lazy override val httpServer = ServerLauncher(httpConfig,
        ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
        ServletContext("/export", "", InitServlet(new ExportServlet(new TransactionHistoryPersistence()(clientNode.db)), "/*")),
        ServletContext(s"/$configName", "", InitServlet(buildDbAccessServlet.get, s"/$configName/*")))

      startHttpServer

      class AnalysisUIProvider extends UIProvider {

        override def getUIClass(event: UIClassSelectionEvent): Class[_ <: UI] = classOf[MainUI]

        override def createInstance(event: UICreateEvent): UI = {
          new MainUI(clientNode)
        }

      }

      def buildUIServlet = new sss.ui.Servlet(new AnalysisUIProvider)
    }

  }


}

