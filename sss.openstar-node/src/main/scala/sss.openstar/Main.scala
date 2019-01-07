package sss.openstar

import sss.openstar.nodebuilder._
import sss.openstar.peers.DiscoveryActor
import sss.openstar.peers.PeerManager.IdQuery
import sss.openstar.quorumledger.QuorumService
import sss.openstar.tools.{TestTransactionSender, TestnetConfiguration}
import sss.openstar.wallet.UtxoTracker

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

      Try(QuorumService.create(globalChainId, "alice", "bob"))

      init // <- init delayed until phrase can be initialised.

      Try(pKTracker.track(nodeIdentity.publicKey))


      startUnsubscribedHandler

      TestnetConfiguration(bootstrapIdentities)

      TestTransactionSender(bootstrapIdentities, wallet)

      peerManager.addQuery(IdQuery(nodeConfig.peersList map (_.nodeId.id)))

      synchronization.startSync

      startHttpServer

      DiscoveryActor(DiscoveryActor.props(discovery))

    }

  }
}



