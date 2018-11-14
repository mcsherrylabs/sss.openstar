package sss.ui.nobu


import java.io.File

import akka.actor.ActorSystem
import com.vaadin.server.{UIClassSelectionEvent, UICreateEvent, UIProvider}
import com.vaadin.ui.UI
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.{HomeDomainBuilder, PartialNode}
import sss.asado.peers.PeerManager.IdQuery
import sss.asado.quorumledger.QuorumService
import sss.asado.wallet.UtxoTracker.NewWallet
import sss.ui.nobu.NobuUI.SessionEnd
import sss.ui.reactor.ReactorActorSystem

import scala.util.Try

/**
  * Created by alan on 6/10/16.
  */
object Main {

  trait ClientNode extends PartialNode with HomeDomainBuilder {
    lazy implicit val blockingWorkers =
      BlockingWorkers(
        new CreateIdentity().createIdentity orElse new SendMessage(() => currentBlockHeight(), conf).sendMessage
      )


    val keyFolder = config.getString("keyfolder")
    new File(keyFolder).mkdirs()

    lazy val users = new UserDirectory(keyFolder)

  }

  def main(withArgs: Array[String]) {


    new ClientNode {

      clientNode: ClientNode =>

      override val phrase: Option[String] = Option(withArgs(0))
      override val configName: String = "node"
      implicit lazy override val actorSystem: ActorSystem = ReactorActorSystem.actorSystem
      Try(QuorumService.create(globalChainId, "bob", "alice", "eve"))

      init // <- init delayed until phrase can be initialised.

      StateActor(clientNode)

      users.listUsers.foreach(u =>
        messageEventBus publish NewWallet(
          buildWalletIndexTracker(u))
      )

      Try(pKTracker.track(nodeIdentity.publicKey))

      startUnsubscribedHandler

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.id)))

      synchronization.startSync

      val httpConfig = DynConfig[ServerConfig]("httpServerConfig")

      lazy override val httpServer = ServerLauncher(httpConfig,
        ServletContext("/", "WebContent", InitServlet(buildUIServlet, "/*")),
        ServletContext("/console", "", InitServlet(buildConsoleServlet.get, "/console/*")),
        ServletContext(s"/$configName/*", "", InitServlet(buildDbAccessServlet.get, s"/$configName/*")),
        ServletContext("/service", ""))

      startHttpServer

      class NobuUIProvider extends UIProvider {

        override def getUIClass(event: UIClassSelectionEvent): Class[_ <: UI] = classOf[NobuUI]

        override def createInstance(event: UICreateEvent): UI = {
          new NobuUI(clientNode)
        }

      }

      def buildUIServlet = new sss.ui.Servlet(new NobuUIProvider, str => {
        messageEventBus.publish(SessionEnd(str))
        str
      })


    }
  }

}

