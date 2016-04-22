package sss.asado.tools

import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.agent.Agent
import com.google.common.primitives.Longs
import com.typesafe.config.Config
import ledger.{SignedTx, StandardTx, TxIndex, TxInput, TxOutput}
import sss.ancillary.Memento
import sss.asado.account.ClientKey
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.{BindControllerSettings, ConnectTo, SendToNetwork}
import sss.asado.network._
import sss.asado.{BaseClient, MessageKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

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
                             args: Array[String]
                            ): Unit = {

    val pka = ClientKey.account
    val firstTxIdHex = args(1)
    val index = args(2).toInt
    val firstAmount = args(3).toInt
    val firstTxId = DatatypeConverter.parseHexBinary(firstTxIdHex)
    val freeIndexes = Memento("freeindexes")

    val ref = actorSystem.actorOf(Props(classOf[TxClientActor], args,peerList: Set[NodeId],freeIndexes,
      connectedPeers: Agent[Set[Connection]], messageRouter, ncRef))

    while (connectedPeers().size == 0) {
      println("Waiting for connection...")
      Thread.sleep(1111)
    }

    ref ! CheckConnection
    peerList.foreach(e => println(s"Connected $e"))

    val firstOne = freeIndexes.read match {
      case Some(txIndexStr) => DatatypeConverter.parseHexBinary(txIndexStr)
      case None => firstTxId

    }
    ref ! FreeTxIndex(TxIndex(firstOne, index), firstAmount)
  }
}
case object CheckConnection
case class FreeTxIndex(txIndx: TxIndex, amount: Int)

class TxClientActor(args: Array[String],peerList: Set[NodeId], freeIndexes: Memento,
                    connectedPeers: Agent[Set[Connection]], messageRouter: ActorRef, ncRef: ActorRef) extends Actor with ActorLogging {

  import block._

  context.system.scheduler.schedule(5 seconds, 5 seconds, self, "EXIT")
  messageRouter ! Register(MessageKeys.SignedTxAck)
  messageRouter ! Register(MessageKeys.SignedTxNack)
  messageRouter ! Register(MessageKeys.AckConfirmTx)
  messageRouter ! Register(MessageKeys.NackConfirmTx)

  val pka = ClientKey.account

  var alltransmitted : Set[TxIndex] = Set()

  def createTx(txIndex: TxIndex, amunt: Int): SignedTx = {
    val txOutput = TxOutput(amunt, SinglePrivateKey(pka.publicKey))
    val txInput = TxInput(txIndex, amunt, PrivateKeySig)
    val tx = StandardTx(Seq(txInput), Seq(txOutput))
    val sig = tx.sign(pka)
    SignedTx(tx, Seq(sig))
  }

  override def receive: Receive = {
    case NetworkMessage(MessageKeys.SignedTxAck, bytes) => {
      val blcok = Longs.fromByteArray(bytes)
      println(s"Tx in $blcok")
    }

    case FreeTxIndex(txIndex, amount) =>
      val newTx = createTx(txIndex, amount)
      val newTxIndx = TxIndex(newTx.txId, 0)
      alltransmitted += newTxIndx
      freeIndexes.write(DatatypeConverter.printHexBinary(newTx.txId), false)
      ncRef ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, newTx.toBytes))



    case CheckConnection =>
      if (connectedPeers().size < peerList.size) peerList foreach (ncRef ! ConnectTo(_))
      context.system.scheduler.scheduleOnce(5 seconds, self, CheckConnection)

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      println(new String(bytes))
      freeIndexes.read map { txIndexStr =>
        context.system.scheduler.scheduleOnce(500 millis, self,  FreeTxIndex(TxIndex(DatatypeConverter.parseHexBinary(txIndexStr), 0), 10))
      }

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val confirmed = bytes.toBlockChainIdTx
      println(s"Confirmation $confirmed")
      alltransmitted.find(_ == TxIndex(confirmed.blockTxId.txId, 0)) map { nextIndex =>
        alltransmitted -= TxIndex(confirmed.blockTxId.txId, 0)
        //self !  FreeTxIndex(nextIndex, 10)
        context.system.scheduler.scheduleOnce(750 millis, self,  FreeTxIndex(nextIndex, 10))
      }

    case "EXIT" => Memento("exit").read match {
      case Some(_) =>
        context stop self
        context.system.terminate()

      case x =>
    }

    case x => log.info(s"No idea $x")
  }
}

