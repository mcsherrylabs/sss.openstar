package sss.asado

import sss.asado.nodebuilder._
import sss.asado.peers.PeerManager.IdQuery
import sss.asado.quorumledger.QuorumService
import sss.asado.tools.{TestTransactionSender, TestnetConfiguration}
import sss.asado.wallet.UtxoTracker

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

      UtxoTracker(wallet)

      startUnsubscribedHandler

      TestnetConfiguration(bootstrapIdentities)

      TestTransactionSender(bootstrapIdentities, wallet)

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.id)))

      synchronization.startSync

      startHttpServer


    }

  }
}



