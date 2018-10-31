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
import sss.asado.tools.{TestTransactionSender, TestnetConfiguration}
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

      Try(QuorumService.create(globalChainId, "bob", "alice", "eve"))

      init // <- init delayed until phrase can be initialised.

      Try(pKTracker.track(nodeIdentity.publicKey))

      startUnsubscribedHandler

      TestnetConfiguration(bootstrapIdentities)

      TestTransactionSender(bootstrapIdentities, wallet)

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.id)))

      synchronization.startSync

      startHttpServer


    }

  }
}



