package sss.asado

import java.util.logging.{Level, Logger}

import akka.actor.{ActorContext, ActorRef, Props}
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.chains.ChainSynchronizer.StartSyncer
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
      with EncoderBuilder
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
          sendTo,
          messageEventBus,
          bc, db, chain.ledgers, chain.id)

      import chain.ledgers


      startHttpServer
      startUnsubscribedHandler


      val qm = QuorumMonitor(messageEventBus, globalChainId, nodeIdentity.id, chain.quorumCandidates(), peerManager)
      val synchronization = new ChainSynchronizer(messageEventBus, chain.id, chain.quorumCandidates(), nodeIdentity.id, startSyncer)

      LeaderElectionActor(nodeIdentity.id, messageEventBus, sendToMany, bc)

      ChainDownloadResponseActor(sendTo, messageEventBus, nodeConfig.blockChainSettings.maxSignatures, bc)

      QuorumFollowersSyncedMonitor(messageEventBus, nodeIdentity.id, bc, sendTo)

      TxWriterActor(TxWriterActor.props(nodeConfig.blockChainSettings, messageEventBus, nodeIdentity.id,bc, net.send, processCoinBaseHook))

      TxDistributeeActor(TxDistributeeActor.props(messageEventBus , sendTo, bc, nodeIdentity))

      synchronization.startSync
      synchronization.queryStatus
      qm.queryQuorum

    }

  }
}



