package sss.asado.nodebuilder

import sss.ancillary.{DynConfig, InitServlet, ServerConfig, ServerLauncher}
import sss.asado.console.ConsoleServlet
import sss.asado.http.ClaimServlet

/**
  * Created by alan on 6/16/16.
  */
trait HttpServerBuilder {

  self: NodeConfigBuilder with
          BlockChainBuilder with
          DbBuilder with
          ActorSystemBuilder with
          MessageRouterActorBuilder with
          IdentityServiceBuilder with
          WalletBuilder with
          NetworkContollerBuilder =>

  lazy val httpServer =  {
    ServerLauncher.singleContext(DynConfig[ServerConfig](nodeConfig.conf.getConfig("httpServerConfig")))
  }

  def startHttpServer: Unit = {
    configureServlets
    httpServer.start
  }

  def buildConsoleServlet: Option[ConsoleServlet] = {
    Option(new ConsoleServlet(nodeConfig.connectedPeers, ncRef, identityService, wallet, db))
  }


  def configureServlets = {
    buildConsoleServlet map (let => httpServer.addServlet(InitServlet(let, "/console/*")))
  }

}

trait ClaimServletBuilder {

  self: NodeConfigBuilder with
    MessageRouterActorBuilder with
    ActorSystemBuilder with
    IntegratedWalletBuilder with
    HttpServerBuilder =>


  def buildClaimServlet: Option[ClaimServlet] = {
    Option(new ClaimServlet(actorSystem, messageRouterActor, integratedWallet))
  }

  def addClaimServlet = {
    buildClaimServlet map (let => httpServer.addServlet(InitServlet(let, "/claim/*")))
  }

}