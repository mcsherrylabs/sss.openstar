package sss.asado.nodebuilder

import org.hsqldb.server.HsqlServlet
import sss.ancillary.{DynConfig, InitServlet, ServerConfig, ServerLauncher}
import sss.asado.console.ConsoleServlet
import sss.asado.http.ClaimServlet

import collection.JavaConverters._

/**
  * Created by alan on 6/16/16.
  */
trait HttpServerBuilder {

  self: NodeConfigBuilder with
          BlockChainBuilder with
          RequireDb with
          NodeIdentityBuilder with
          RequireActorSystem with
          MessageEventBusBuilder with
          ChainBuilder with
          IdentityServiceBuilder with
          SendTxBuilder with
          ConfigBuilder with
          ClaimServletBuilder with
          NetworkControllerBuilder =>

  lazy val httpServer =  {
    ServerLauncher.singleContext(DynConfig[ServerConfig](nodeConfig.conf.getConfig("httpServerConfig")))
  }

  def startHttpServer: Unit = {
    configureServlets
    addClaimServlet
    httpServer.start
  }

  def buildConsoleServlet: Option[ConsoleServlet] = {
    Option(new ConsoleServlet(net, messageEventBus, nodeIdentity, () => chain.quorumCandidates(), identityService, sendTx)(db))
  }


  def buildDbAccessServlet: Option[HsqlServlet] = {
    val dataFolder = config.getString("datafolder")
    Option(new org.hsqldb.server.HsqlServlet(s"file:${dataFolder}sss-hsql-ledger-${configName}"))
  }

  def configureServlets = {
    buildConsoleServlet map (let => httpServer.addServlet(InitServlet(let, "/console/*")))
    buildDbAccessServlet map (s => httpServer.addServlet(InitServlet(s, s"/$configName/*")))
  }

}

trait ClaimServletBuilder {


  self: NodeConfigBuilder with
    MessageEventBusBuilder with
    RequireActorSystem with
    WalletBuilder with
    BalanceLedgerBuilder with
    HttpServerBuilder =>


  def buildClaimServlet: Option[ClaimServlet] = {
    Option(new ClaimServlet(messageEventBus, balanceLedger,wallet))
  }
   def addClaimServlet = {
    buildClaimServlet map (let => httpServer.addServlet(InitServlet(let, "/claim/*")))
  }

}
