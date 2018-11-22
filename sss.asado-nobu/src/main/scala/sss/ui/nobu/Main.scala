package sss.ui.nobu


import java.io.File
import java.util.function.UnaryOperator

import akka.actor.{ActorRef, ActorSystem}
import com.vaadin.server.{UIClassSelectionEvent, UICreateEvent, UIProvider}
import com.vaadin.ui.UI
import sss.ancillary.{DynConfig, _}
import sss.asado.nodebuilder.{HomeDomainBuilder, PartialNode}
import sss.asado.peers.PeerManager.IdQuery
import sss.asado.quorumledger.QuorumService
import sss.asado.wallet.UtxoTracker.NewWallet
import sss.ui.GenericUIProvider
import sss.ui.nobu.NobuUI.SessionEnd

import scala.util.Try

/**
  * Created by alan on 6/10/16.
  */
object Main {

  trait ClientNode extends PartialNode with HomeDomainBuilder {

    val keyFolder = config.getString("keyfolder")
    new File(keyFolder).mkdirs()

    lazy val users = new UserDirectory(keyFolder)
    lazy implicit val confImp = conf
    implicit val currentBlockHeightImp = () => currentBlockHeight()

  }

  def main(withArgs: Array[String]) {


    new ClientNode {

      clientNode: ClientNode =>

      override val phrase: Option[String] = Option(withArgs(0))
      override val configName: String = "node"

      Try(QuorumService.create(globalChainId, "bob", "alice", "eve"))

      init // <- init delayed until phrase can be initialised.


      BlockingWorkers(
        new CreateIdentity(users, buildWallet).createIdentity
          orElse new SendMessage(() => currentBlockHeight(), conf).sendMessage
      )

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
        ServletContext(s"/$configName", "", InitServlet(buildDbAccessServlet.get, s"/$configName/*")),
        ServletContext("/service", ""))

      val provider = GenericUIProvider(classOf[NobuUI], _ => new NobuUI(clientNode))

      startHttpServer

      def buildUIServlet = new sss.ui.Servlet(provider, (str => {
        messageEventBus.publish(SessionEnd(str))
          str}): UnaryOperator[String]
      )


    }
  }

}

