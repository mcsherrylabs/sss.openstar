package sss.analysis

import akka.actor.{ActorSystem, Props}
import com.vaadin.server.{UIClassSelectionEvent, UICreateEvent, UIProvider}
import com.vaadin.ui.UI
import org.joda.time.format.DateTimeFormat
import sss.ancillary.{DynConfig, _}
import sss.openstar.nodebuilder.{HomeDomainBuilder, PartialNode}
import sss.openstar.peers.PeerManager.IdQuery
import sss.openstar.quorumledger.QuorumService
import sss.ui.{ExportServlet, GenericUIProvider, MainUI}

import scala.util.Try

/**
  * Created by alan on 6/10/16.
  */
object Main {

  trait ClientNode extends PartialNode with HomeDomainBuilder

  val dateFormat =  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")

  def main(args: Array[String]) {

    new ClientNode {

      clientNode: ClientNode =>

      override val phrase: Option[String] = Option("password")
      override val configName: String = "analysis"

      Try(QuorumService.create(globalChainId, "bob", "alice", "eve"))

      init // <- init delayed until phrase can be initialised.

      startUnsubscribedHandler

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.nodeId.id)))

      clientNode.actorSystem.actorOf(Props(classOf[AnalysingActor], clientNode).withDispatcher("my-pinned-dispatcher"))

      synchronization.startSync

      val httpConfig = DynConfig[ServerConfig]("httpServerConfig")

      lazy override val httpServer = ServerLauncher(httpConfig,
        ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
        ServletContext("/export", "", InitServlet(new ExportServlet(new TransactionHistoryPersistence()(clientNode.db)), "/*")),
        ServletContext(s"/$configName", "", InitServlet(buildDbAccessServlet.get, s"/$configName/*")))

      startHttpServer

      def buildUIServlet = new sss.ui.Servlet(GenericUIProvider(classOf[MainUI], _ => new MainUI(clientNode)))
    }

  }


}

