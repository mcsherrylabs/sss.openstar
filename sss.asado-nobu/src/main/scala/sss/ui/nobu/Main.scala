package sss.ui.nobu


import akka.actor.{ActorRef, ActorSystem, Props}
import com.vaadin.server.{UIClassSelectionEvent, UICreateEvent, UIProvider}
import com.vaadin.ui.UI
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.{HomeDomainBuilder, PartialNode}
import sss.asado.peers.PeerManager.IdQuery
import sss.asado.quorumledger.QuorumService
import sss.ui.reactor.ReactorActorSystem

import scala.concurrent.Future
import scala.util.Try

/**
  * Created by alan on 6/10/16.
  */
object Main {

  trait ClientNode extends PartialNode with HomeDomainBuilder

  var clientEventActor: ActorRef = _


  def main(withArgs: Array[String]) {


    new ClientNode {

      clientNode =>

      override val phrase: Option[String] = Some("fpaifpai33")
      override val configName: String = "node"
      lazy override val actorSystem: ActorSystem = ReactorActorSystem.actorSystem
      Try(QuorumService.create(globalChainId, "bob"))

      init // <- init delayed until phrase can be initialised.


      Try(pKTracker.track(nodeIdentity.publicKey))

      startUnsubscribedHandler

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.id)))

      synchronization.startSync

      val httpConfig = DynConfig[ServerConfig]("httpServerConfig")

      lazy override val httpServer = ServerLauncher(httpConfig,
        ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
        ServletContext("/console", "", InitServlet(buildConsoleServlet.get, "/console/*")),
        ServletContext("/service", ""))

      startHttpServer


      class NobuUIProvider extends UIProvider {


        override def getUIClass(event: UIClassSelectionEvent): Class[_ <: UI] = classOf[NobuUI]

        override def createInstance(event: UICreateEvent): UI = {
          new NobuUI(clientNode)
        }
      }

      def buildUIServlet = new sss.ui.Servlet(new NobuUIProvider)

    }
  }

}

