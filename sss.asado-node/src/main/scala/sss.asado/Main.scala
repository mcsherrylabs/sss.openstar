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
object Main {

  def main(withArgs: Array[String]): Unit = {

    val aNewHope = new PartialNode {

      override val configName: String = withArgs(0)

      override val phrase: Option[String] =
        if (withArgs.length > 1) Option(withArgs(1)) else None

      Try(QuorumService.create(globalChainId, "bob"))

      init // <- init delayed until phrase can be initialised.

      Try(pKTracker.track(nodeIdentity.publicKey))

      startUnsubscribedHandler

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.id)))

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



