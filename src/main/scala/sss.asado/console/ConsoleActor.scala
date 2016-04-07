package sss.asado.console

import java.net.InetSocketAddress
import javax.xml.bind.DatatypeConverter

import akka.actor.{Actor, ActorRef}
import akka.agent.Agent
import com.google.common.primitives.Longs
import ledger._
import sss.asado.MessageKeys
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.ledger.{UTXODBStorage, UTXOLedger}
import sss.asado.network.MessageRouter.{Register, UnRegister}
import sss.asado.network.NetworkController._
import sss.asado.network.{NetworkMessage, NodeId}
import sss.asado.util.{ClientKey, Console}
import sss.db.{Db, Where}

import scala.util.{Failure, Success, Try}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */

trait ConsolePattern {
  val connectPeerPattern = """connect (.*):(\d\d\d\d)""".r
  val peerPattern = """(.*):(\d\d\d\d)""".r
  val blockHeaders = """blocks ([0-9]+)""".r
  val repeatTx = """repeat tx (.*) ([0-9]+) ([0-9]+) ([0-9]+)""".r
}

case class NoRead(cmd: String)

class ConsoleActor(args: Array[String], msgRouter: ActorRef,
                   nc: ActorRef,
                   peerList: Agent[List[InetSocketAddress]],
                   implicit val db: Db) extends Actor with Console with ConsolePattern {

  lazy val utxos = new UTXOLedger(new UTXODBStorage())
  lazy val utxosTable = db.table("utxo")
  lazy val blocks = db.table("blockchain")

  var sessionData: Map[String, Any] = {
    val newMap = Map[String, PrivateKeyAccount]("client" -> ClientKey.account)
    Map("keys" -> newMap)
  }

  def printHelp(): Unit = {
    println("Shur I dono ta fu ...")
    println("Try 'quit'")
  }

  val console: PartialFunction[String, Unit] = {

    case "init" => println("Asado node console ready ... ")

    case "help" => {
      printHelp()
    }

    case blockHeaders(count) => {
      println(s"Total blocks ${blocks.count}, printing $count")
      blocks.filter(Where("id > 0 ORDER BY height DESC LIMIT ?", count)).foreach(println)
    }

    case "utxo" => {
      println(s"Total utxos ${utxosTable.count}, printing ..")
      var count = 0
      utxosTable.filter(Where("indx > -1 LIMIT ?", 100)) map { r =>
        val tmp = TxIndex(DatatypeConverter.parseHexBinary(r[String]("txid")), r[Int]("indx"))
        utxos.entry(tmp) match {
          case None =>
          case Some(entry) => println(s"$tmp ${entry.amount}")
        }
      }
    }

    case "new keys" => {

      val keys = PrivateKeyAccount()

      sessionData.get("keys") match {
        case None => {
          val newMap = Map[String, PrivateKeyAccount]() + (read[String]("friendly name? ") -> keys)
          sessionData += "keys" -> newMap
          println(keys)
        }
        case Some(ks) => {
          val newMap = ks.asInstanceOf[Map[String, PrivateKeyAccount]] + (read[String]("friendly name? ") -> keys)
          sessionData += "keys" -> newMap
          println(keys)
        }
      }

    }

    case connectPeerPattern(ip, port) => nc ! ConnectTo(NodeId("Some place" , InetSocketAddress.createUnresolved(ip, port.toInt)))

    case "list keys" => {
      sessionData.getOrElse("keys", println("No keys")).asInstanceOf[Map[String, PrivateKeyAccount]].foreach(println(_))
    }


    case "unload tx" => {
      sessionData -= "tx"
    }

    case "save tx" => {
      sessionData.get("tx") match {
        case None => println("There's none");
        case Some(stx: SignedTx) => {
          //ledger(stx)
          println("Ledgered");
          sessionData -= "tx"
        }
        case Some(x) => println("Programming error");
      }
    }

    case "broadcast test" => nc ! SendToNetwork(NetworkMessage(1, "TESTING123".getBytes))

    case "broadcast tx" => {
      sessionData.get("tx") match {
        case None => println("There's none");
        case Some(stx: SignedTx) => {
          nc ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, stx.toBytes))
        }
        case Some(x) => println("Programming error");
      }
    }

    case "peers" => peerList.foreach(println)

    case repeatTx(txidHex, index, amount, count) =>
      val pka = ClientKey.account
      //val txIdStr = read[String]("txId? ")
        val txId =
          DatatypeConverter.parseHexBinary(txidHex)

        //CC1B6D52BCD9EB694D6F726EF9A05002256D65BACC879B9CBF9516A05FB5DDD9
        val txIndex = TxIndex(txId, index.toInt)
        val newAmount = amount.toInt / 2
        val txOutput = TxOutput(newAmount, SinglePrivateKey(pka.publicKey))
        val txOutput2 = TxOutput(newAmount, SinglePrivateKey(pka.publicKey))
        val txInput = TxInput(txIndex, amount.toInt, PrivateKeySig)
        val tx = StandardTx(Seq(txInput), Seq(txOutput, txOutput2))
        val sig = tx.sign(pka)
        val stx = SignedTx(tx, Seq(sig))
        println(s"Splitting ${amount} in 2 from ${DatatypeConverter.printHexBinary(txIndex.txId)} to ${DatatypeConverter.printHexBinary(tx.txId)}")
        sessionData += ("lasttx" -> stx)
        nc ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, stx.toBytes))
        val newSend = s"repeat tx ${DatatypeConverter.printHexBinary(tx.txId)} 0 $newAmount ${count.toInt - 1}"
        if(count.toInt > 0) {

          println(s"Sending $newSend")
          self ! newSend
        }

    case "auto tx" => {

      val pka = ClientKey.account
      //val txIdStr = read[String]("txId? ")

      val txId = DatatypeConverter.parseHexBinary("47454E495345533147454E495345533147454E495345533147454E4953455331")
      val txIndex = TxIndex(txId, 0)
      val txOutput = TxOutput(1000, SinglePrivateKey(pka.publicKey))
      val txInput = TxInput(txIndex, 1000, PrivateKeySig)
      val tx = StandardTx(Seq(txInput), Seq(txOutput))
      val sig = tx.sign(pka)
      println(tx)
      val stx = SignedTx(tx, Seq(sig))
      sessionData += ("lasttx" -> stx)
      nc ! SendToNetwork(NetworkMessage(MessageKeys.SignedTx, stx.toBytes))
    }

    case "tx" => {

      sessionData.get("tx") match {
        case None => {
          val keysName = read[String]("Friendly name of keys? ")
          val pka = sessionData("keys").asInstanceOf[Map[String, PrivateKeyAccount]](keysName)
          //val txIdStr = read[String]("txId? ")
          val txId = DatatypeConverter.parseHexBinary("47454E495345533147454E495345533147454E495345533147454E4953455331")
          val txIndex = TxIndex(txId, read[Int]("Index? (0) "))
          val txOutput = TxOutput(read[Int]("Output Amount? "), SinglePrivateKey(pka.publicKey))
          val txInput = TxInput(txIndex, read[Int]("Input amount? (Same as output amount) "), PrivateKeySig)
          val tx = StandardTx(Seq(txInput), Seq(txOutput))
          val sig = tx.sign(pka)
          println(tx)
          val stx = SignedTx(tx, Seq(sig))
          println("Decumbred ok? " + stx.tx.outs(0).encumbrance.decumber(stx.txId +: stx.params, txInput.sig))
          sessionData += ("tx" -> stx)

        }
        case Some(tx) => println(tx)
      }
    }

    case "quit" => println("G'luk!"); context.system.terminate

    case catchall =>  println(catchall)


  }


  val Wrapper = new PartialFunction[Any, Unit] {
    def isDefinedAt(x: Any): Boolean = x match {
      case x: String => console.isDefinedAt(x)
      case NoRead(cmd) => true
      case _ => false
    }
    def apply(str: Any): Unit = {

      str match {
        case NoRead(cmd) => console(cmd)
        case isStr: String => {

          Try {
            console(isStr)

          } match {
            case Failure(e) => {
              println(e)
              println("Say what now? ")
            }
            case Success(_) =>
          }
          self ! read[String]()
        }
      }
    }
  }

  override def receive: Receive = Wrapper

}

class InfoActor(messageRouter: ActorRef) extends Actor {
  override def receive: Actor.Receive = {
    case r @ Register(msg) => messageRouter ! r
    case r @ UnRegister(msg) => messageRouter ! r
    case NetworkMessage(MessageKeys.SignedTxAck, testBytes) => println("In block number " + Longs.fromByteArray(testBytes))
    case NetworkMessage(MessageKeys.SignedTxNack, testBytes) => println(new String(testBytes))
    case NetworkMessage(code, txBytes) => {
      println(s"Got $code, deserialise bytes...")
      val signedTx = txBytes.toSignedTx
      println(s"$signedTx")
    }
  }
}