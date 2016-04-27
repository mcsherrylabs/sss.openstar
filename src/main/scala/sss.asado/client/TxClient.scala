package sss.asado.client

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.typesafe.config.Config
import sss.asado.BaseClient
import sss.asado.account.ClientKey
import sss.asado.client.wallet.WalletPersistence
import sss.asado.network.NetworkController.BindControllerSettings
import sss.asado.network._
import sss.db.Db

import scala.language.{implicitConversions, postfixOps}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */


object TxClient extends BaseClient {

  override protected def run(settings: BindControllerSettings,
                             actorSystem: ActorSystem,
                             peerList: Set[NodeId],
                             connectedPeers: Agent[Set[Connection]],
                             messageRouter: ActorRef,
                             ncRef: ActorRef,
                             nodeConfig: Config,
                             args: Array[String],
                             db: Db
                            ): Unit = {

    val wallet = new WalletPersistence(db)

    val pka = args match {
      case Array(clientName) => ClientKey(clientName)
      case Array(clientName, unspentTxIdVarChar, unspentIndex, amount) =>
        val added = wallet.addUnspent(unspentTxIdVarChar, unspentIndex.toInt, amount.toInt)

        ClientKey(clientName)
    }


    val ref = actorSystem.actorOf(Props(classOf[WalletActor], args,peerList: Set[NodeId],
      connectedPeers: Agent[Set[Connection]], messageRouter, ncRef))

    while (connectedPeers().size == 0) {
      println("Waiting for connection...")
      Thread.sleep(1111)
    }

    peerList.foreach(e => println(s"Connected $e"))

  }
}



