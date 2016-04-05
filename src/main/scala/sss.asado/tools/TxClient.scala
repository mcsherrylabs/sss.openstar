package sss.asado.tools

import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.google.common.primitives.Longs
import com.typesafe.config.Config
import ledger.{SignedTx, StandardTx, TxIndex, TxInput, TxOutput}
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.{BindControllerSettings, SendToNetwork}
import sss.asado.network._
import sss.asado.util.ClientKey
import sss.asado.{BaseClient, MessageKeys}

import scala.io.StdIn
import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object TxClient extends BaseClient {


  override protected def run(settings: BindControllerSettings,
                             actorSystem: ActorSystem,
                             peerList: Agent[Set[Connection]],
                             messageRouter: ActorRef,
                             ncRef: ActorRef,
                             nodeConfig: Config,
                             args: Array[String]
                            ): Unit = {

    val pka = ClientKey.account
    val firstTxIdHex = args(1)
    val index = args(2).toInt
    val firstAmount = args(3).toInt
    val firstTxId = DatatypeConverter.parseHexBinary(firstTxIdHex )

    val ref = actorSystem.actorOf(Props(classOf[TxClientActor], args, messageRouter, ncRef))

    while(peerList().size == 0) {
      println("Waiting for connection...")
      Thread.sleep(1111)
    }

    peerList.foreach (e => println(s"Connected $e"))

    go(TxIndex(firstTxId, index), firstAmount)

    def go(txIndex: TxIndex, amount: Int): Unit = {
        val newTx = createTx(txIndex, amount)
        ncRef ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, newTx.toBytes))
        StdIn.readLine match {
          case "q" =>
          case _ => go(TxIndex(newTx.txId, 0), amount)
        }
    }

    def createTx(txIndex: TxIndex, amunt: Int): SignedTx = {
      val txOutput = TxOutput(amunt, SinglePrivateKey(pka.publicKey))
      val txInput = TxInput(txIndex, amunt, PrivateKeySig)
      val tx = StandardTx(Seq(txInput), Seq(txOutput))
      val sig = tx.sign(pka)
      SignedTx(tx, Seq(sig))
    }
  }

}

class TxClientActor(args :Array[String], messageRouter: ActorRef, ncRef : ActorRef) extends Actor with ActorLogging {

  import block._
  messageRouter ! Register(MessageKeys.SignedTxAck)
  messageRouter ! Register(MessageKeys.SignedTxNack)
  messageRouter ! Register(MessageKeys.AckConfirmTx)

  override def receive: Receive = {
    case NetworkMessage(MessageKeys.SignedTxAck, bytes) => {
      val blcok = Longs.fromByteArray(bytes)
      println(s"Tx in $blcok")
    }

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) => println(new String(bytes))

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val confirmed = bytes.toAckConfirmTx
      println(s"Confirmation $confirmed")


  }
}

