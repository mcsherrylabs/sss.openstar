package sss.asado

import java.util.logging.{Level, Logger}

import akka.actor.{ActorContext, ActorRef, Props}
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains._
import sss.asado.network.{MessageEventBus, NetSendToMany, NetworkRef}
import sss.asado.nodebuilder._
import sss.asado.peers.PeerManager.{IdQuery, PeerConnection}

import scala.language.postfixOps


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */

case class InitWithActorRefs(refs: ActorRef*)

  object CoreMain {
    def main(withArgs: Array[String]): Unit = {

      val core = new CoreNode {
        override val configName: String = withArgs(0)
        override val phrase: Option[String] = {
          if(withArgs.length > 1) Option(withArgs(1))
          else None
        }
      }
      core.initStateMachine
      core.net
      core.startHttpServer
      core.initSimplePageTxActor

    }
  }

  object ServicesMain {
    def main(withArgs: Array[String]): Unit = {

      val core = new ServicesNode {
        override val configName: String = withArgs(0)
        override val phrase: Option[String] = if(withArgs.length > 1) Option(withArgs(1)) else None

      }
      core.initStateMachine
      core.messageServiceActor
      core.addClaimServlet
      core.net
      core.startHttpServer
      core.initSimplePageTxActor

    }
  }

object ANewHopeMain {

  def main(withArgs: Array[String]): Unit = {

    val aNewHope = new Logging
      with ConfigBuilder
      with RequireActorSystem
      with DbBuilder
      with RequireGlobalChainId
      with NodeConfigBuilder
      with RequirePhrase
      with RequireSeedBytes
      with NodeIdentityBuilder
      with IdentityServiceBuilder
      with BootstrapIdentitiesBuilder
      with DecoderBuilder
      with MessageEventBusBuilder
      with BlockChainBuilder
      with NetworkInterfaceBuilder
      with HandshakeGeneratorBuilder
      with NetworkControllerBuilder
      with BalanceLedgerBuilder
      with PeerManagerBuilder
      with HttpServerBuilder
      with UnsubscribedMessageHandlerBuilder
      with WalletBuilder
      with WalletPersistenceBuilder
      with CoinbaseGeneratorBuilder
      with ChainBuilder {

      override val configName: String = withArgs(0)

      override val phrase: Option[String] =
        if (withArgs.length > 1) Option(withArgs(1)) else None

      def shutdown: Unit = {
        actorSystem.terminate
      }

      Logger.getLogger("hsqldb.db").setLevel(Level.OFF)

      def startSyncer(context: ActorContext, peerConnection: PeerConnection): Unit= {
        import chain.ledgers

        val chainDownloadRequestActorProps = ChainDownloadRequestActor.props(peerConnection,
          nodeIdentity,
          net.send,
          messageEventBus,
          bc)

        ChainDownloadRequestActor(chainDownloadRequestActorProps)(context)
      }


      import chain.ledgers


      startHttpServer
      startUnsubscribedHandler


      val qm = QuorumMonitor(messageEventBus, globalChainId, nodeIdentity.id, chain.quorumCandidates(), peerManager)
      val synchronization = new ChainSynchronizer(messageEventBus, chain.id, chain.quorumCandidates(), nodeIdentity.id, startSyncer)

      LeaderElectionActor(nodeIdentity.id, messageEventBus, net.send: NetSendToMany, bc)

      ChainDownloadResponseActor(net.send, messageEventBus, nodeConfig.blockChainSettings.maxSignatures, bc)

      QuorumFollowersSyncedMonitor(messageEventBus, nodeIdentity.id, bc, net.send)

      TxWriterActor(TxWriterActor.props(nodeConfig.blockChainSettings, messageEventBus, nodeIdentity.id,bc, net.send, processCoinBaseHook))

      TxDistributeeActor(TxDistributeeActor.props(messageEventBus , net.send, bc, nodeIdentity))

      //peerManager.addQuery(IdQuery(chain.quorumCandidates()))
      synchronization.startSync
      synchronization.queryStatus
      qm.queryQuorum

    }

  }
}



