package sss.asado.tools

import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.agent.Agent
import block._
import com.google.common.primitives.Longs
import com.typesafe.config.Config
import ledger.{SignedTx, StandardTx, TxIndex, TxInput, TxOutput}
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.{BindControllerSettings, SendToNetwork}
import sss.asado.network._
import sss.asado.util.ClientKey
import sss.asado.{BaseClient, MessageKeys}

import scala.language.postfixOps

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */
object TxBlasterClient extends BaseClient {


  override protected def run(settings: BindControllerSettings,
                             actorSystem: ActorSystem,
                             peerList: Set[NodeId],
                             connectedPeers: Agent[Set[Connection]],
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

    val ref = actorSystem.actorOf(Props(classOf[TxBlasterActor], args, messageRouter, ncRef))

    while(connectedPeers().size == 0) {
      println("Waiting for connection...")
      Thread.sleep(1111)
    }

    peerList.foreach (e => println(s"Connected $e"))

    go(Seq(TxIndex(firstTxId, index)), firstAmount)

    def go(txIndexs: Seq[TxIndex], amount: Int): Unit = {

      val newAmount: Int = Math.floor(amount / 2).toInt

      if(newAmount == 0) joinTxs(txIndexs, 1)
      else {
        val newTxs = txIndexs flatMap { txId =>
          val newTx = createSplitTx(txId, newAmount)
          ncRef ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, newTx.toBytes))
          println(s"Splitting $newTx")
          var acc = -1
          newTx.tx.outs.map { out =>
            acc += 1
            TxIndex(newTx.txId, acc)
          }
        }

        Thread.sleep(20000)
        go(newTxs, newAmount)
      }
    }

    def joinTxs(txIndexs: Seq[TxIndex], amunt: Int): Unit = {
      if(txIndexs.size > 1){
        val newTxs = txIndexs.grouped(2) flatMap { grp =>
          val a = grp(0)
          val b = grp(1)
          val txInput1 = TxInput(a, amunt, PrivateKeySig)
          val txInput2 = TxInput(b, amunt, PrivateKeySig)
          val txOutput = TxOutput(amunt, SinglePrivateKey(pka.publicKey))
          val tx = StandardTx(Seq(txInput1, txInput2), Seq(txOutput))
          val sig = tx.sign(pka)
          val newTx = SignedTx(tx, Seq(sig))
          ncRef ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, newTx.toBytes))
          println(s"Joining $tx")
          var acc = -1
          newTx.tx.outs.map { out =>
            acc += 1
            TxIndex(newTx.txId, acc)
          }
        }

        Thread.sleep(20000)
        joinTxs(newTxs.toSeq, amunt * 2)
      }
    }

    def createSplitTx(txIndex: TxIndex, amunt: Int): SignedTx = {
      val txOutput = TxOutput(amunt, SinglePrivateKey(pka.publicKey))
      val txOutput2 = TxOutput(amunt, SinglePrivateKey(pka.publicKey))
      val txInput = TxInput(txIndex, amunt * 2, PrivateKeySig)
      val tx = StandardTx(Seq(txInput), Seq(txOutput, txOutput2))
      val sig = tx.sign(pka)
      SignedTx(tx, Seq(sig))
    }
  }

}

class TxBlasterActor(args :Array[String], messageRouter: ActorRef, ncRef : ActorRef) extends Actor with ActorLogging {

  messageRouter ! Register(MessageKeys.SignedTxAck)
  messageRouter ! Register(MessageKeys.SignedTxNack)
  messageRouter ! Register(MessageKeys.AckConfirmTx)

  override def receive: Receive = {
    case NetworkMessage(MessageKeys.SignedTxAck, bytes) => {
      val blcok = Longs.fromByteArray(bytes)
      println(s"Tx in $blcok")
    }

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) => println(new String(bytes))

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) => {
      val confirmed = bytes.toBlockChainIdTx
      println(s"Confirmation $confirmed")
    }

  }
}
