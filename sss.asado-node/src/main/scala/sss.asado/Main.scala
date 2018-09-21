package sss.asado

import java.util.logging.{Level, Logger}

import akka.actor.{ActorRef}
import sss.ancillary.Logging
import sss.asado.chains.ChainSynchronizer.StartSyncer
import sss.asado.chains._
import sss.asado.nodebuilder._


import scala.language.postfixOps


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */

case class InitWithActorRefs(refs: ActorRef*)


object Main {

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
      with NetSendBuilder
      with BalanceLedgerBuilder
      with PeerManagerBuilder
      with HttpServerBuilder
      with UnsubscribedMessageHandlerBuilder
      with WalletBuilder
      with WalletPersistenceBuilder
      with CoinbaseGeneratorBuilder
      with ShutdownHookBuilder
      with ChainBuilder {

      override val configName: String = withArgs(0)

      override val phrase: Option[String] =
        if (withArgs.length > 1) Option(withArgs(1)) else None

      override def shutdown: Unit = {
        actorSystem.terminate
      }

      Logger.getLogger("hsqldb.db").setLevel(Level.OFF)


      val startSyncer: StartSyncer = ChainDownloadRequestActor.createStartSyncer(nodeIdentity,
        send,
          messageEventBus,
          bc, db, chain.ledgers, chain.id)

      import chain.ledgers

      startUnsubscribedHandler

      val synchronization = ChainSynchronizer(chain.quorumCandidates(), nodeIdentity.id, startSyncer)


      LeaderElectionActor(nodeIdentity.id, bc)

      ChainDownloadResponseActor(nodeConfig.blockChainSettings.maxSignatures, bc)

      TxWriterActor(TxWriterActor.props(nodeConfig.blockChainSettings, nodeIdentity.id,bc, processCoinBaseHook, nodeIdentity))

      TxDistributeeActor(TxDistributeeActor.props(bc, nodeIdentity))

      QuorumFollowersSyncedMonitor(nodeIdentity.id, bc)

      val qm = QuorumMonitor(messageEventBus, globalChainId, nodeIdentity.id, chain.quorumCandidates(), peerManager)

      synchronization.startSync

      startHttpServer


    }

  }
}



