package sss.asado

import java.util.logging.{Level, Logger}

import akka.actor.ActorRef
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.ancillary.Logging
import sss.asado.block.FindLeader
import sss.asado.block.serialize.FindLeaderSerializer
import sss.asado.chains.ChainSynchronizer.StartSyncer
import sss.asado.chains.TxWriterActor.InternalTxResult
import sss.asado.chains._
import sss.asado.identityledger.Claim
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.nodebuilder._
import sss.asado.peers.PeerManager.IdQuery
import sss.asado.quorumledger.QuorumService
import sss.asado.util.FutureOps._

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try


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
      with SendTxBuilder
      with UnsubscribedMessageHandlerBuilder
      with WalletBuilder
      with WalletPersistenceBuilder
      with ShutdownHookBuilder
      with PublicKeyTrackerBuilder
      with ChainBuilder {

      override val configName: String = withArgs(0)

      override val phrase: Option[String] =
        if (withArgs.length > 1) Option(withArgs(1)) else None


      override def shutdown: Unit = {
        actorSystem.terminate
      }

      Logger.getLogger("hsqldb.db").setLevel(Level.OFF)

      Try(QuorumService.create(globalChainId, "bob"))

      Try(pKTracker.track(nodeIdentity.publicKey))

      val startSyncer: StartSyncer = ChainDownloadRequestActor.createStartSyncer(nodeIdentity,
        send,
          messageEventBus,
          bc, db, chain.ledgers, chain.id)

      import chain.ledgers

      startUnsubscribedHandler

      val synchronization =
        ChainSynchronizer(chain.quorumCandidates(),
          nodeIdentity.id,
          startSyncer,
          () => bc.getLatestCommittedBlockId(),
          () => bc.getLatestRecordedBlockId(),
        )

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.id)))

      LeaderElectionActor(nodeIdentity.id, bc)

      ChainDownloadResponseActor(nodeConfig.blockChainSettings.maxSignatures, bc)

      TxWriterActor(TxWriterActor.props(nodeConfig.blockChainSettings, nodeIdentity.id,bc, nodeIdentity))

      TxDistributeeActor(TxDistributeeActor.props(bc, nodeIdentity))

      QuorumFollowersSyncedMonitor(nodeIdentity.id, bc)

      QuorumMonitor(messageEventBus, globalChainId, nodeIdentity.id, chain.quorumCandidates(), peerManager)

      TxForwarderActor(1000)

      SouthboundTxDistributorActor(
        SouthboundTxDistributorActor.props(nodeIdentity, () => chain.quorumCandidates(),bc,  net.disconnect)
      )


      synchronization.startSync

      startHttpServer

      def claim(nodeId: String, pKey: PublicKey): Future[InternalTxResult] = {
        val tx = Claim(nodeId, pKey)
        val ste = SignedTxEntry(tx.toBytes, Seq())
        val le = LedgerItem(MessageKeys.IdentityLedger, tx.txId, ste.toBytes)
        sendTx(le)
      }

      if(nodeIdentity.id == "bob") {
        println("Claim bob" + claim(nodeIdentity.id, nodeIdentity.publicKey).await())
        Future.sequence(bootstrapIdentities
          .map(bootId => claim(bootId.nodeId, bootId.pKey))).await()
          .map(internalResult =>  println(s"Claim nodeId $internalResult"))
      }

    }

  }
}



