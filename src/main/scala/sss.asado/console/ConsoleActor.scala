package sss.asado.console

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef}
import akka.agent.Agent
import ledger._
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.{PrivateKeySig, SinglePrivateKey}
import sss.asado.network.MessageRouter.{Register, UnRegister}
import sss.asado.network.NetworkController.{ConnectTo, SendToNetwork}
import sss.asado.network.NetworkMessage
import sss.asado.util.Console

import scala.util.{Failure, Success, Try}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/9/16.
  */

trait ConsolePattern {
  val connectPeerPattern = """connect (.*):(\d\d\d\d)""".r
}

case class NoRead(cmd: String)

class ConsoleActor(args: Array[String], msgRouter: ActorRef,
                   nc: ActorRef,
                   peerList: Agent[List[InetSocketAddress]]
                   ) extends Actor with Console with ConsolePattern {

  var sessionData: Map[String, Any] = Map()

  def printHelp(): Unit = {
    println("Shur I dono ta fu ...")
    println("Try 'quit'")
  }

  val console: PartialFunction[String, Unit] = {

    case "init" => println("Asado node console ready ... ")

    case "help" => {
      printHelp()
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

    case connectPeerPattern(ip, port) => nc ! ConnectTo(InetSocketAddress.createUnresolved(ip, port.toInt))

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
          nc ! SendToNetwork(NetworkMessage(2, stx.toBytes))
        }
        case Some(x) => println("Programming error");
      }
    }

    case "peers" => peerList.foreach(println)

    case "tx" => {

      sessionData.get("tx") match {
        case None => {
          val keysName = read[String]("Friendly name of keys? ")
          val pka = sessionData("keys").asInstanceOf[Map[String, PrivateKeyAccount]](keysName)
          val txId = read[String]("txId? ")
          val txIndex = TxIndex(txId.getBytes, read[Int]("Index? (0) "))
          val txOutput = TxOutput(read[Int]("Output Amount? "), SinglePrivateKey(pka.publicKey))
          val txInput = TxInput(txIndex, read[Int]("Input amount? (Same as output amount) "), PrivateKeySig)
          val tx = StandardTx(Seq(txInput), Seq(txOutput))
          val sig = tx.sign(pka)
          println(tx)
          sessionData += "tx" -> SignedTx(tx, Seq(sig))

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
    case NetworkMessage(1, testBytes) => println(new String(testBytes))
    case NetworkMessage(code, txBytes) => {
      println(s"Got $code, deserialise bytes...")
      val signedTx = txBytes.toSignedTx
      println(s"$signedTx")
    }
  }
}