package sss.analysis

import akka.actor.{Actor, ActorRef, Props}


import scala.concurrent.ExecutionContext.Implicits.global

import sss.asado.ledger._
import sss.asado.balanceledger._
import sss.asado.block.Block
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{ReadyStateEvent, StateMachineInitialised}

import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by alan on 8/17/16.
  */
object Main {

  def main(args: Array[String]): Unit = {

    new ClientNode {
      override val phrase: Option[String] = Some("password")
      override val configName: String = "analysis"

      override lazy val eventListener: ActorRef =
        actorSystem.actorOf(Props(classOf[OrchestratingActor], this))

    }.initStateMachine
  }
}


object Analysis {

  def isCoinBase(input: TxInput): Boolean = {
    input.txIndex.txId sameElements(CoinbaseTxId)
  }

  def analyse(b: Block, currentOuts: Seq[TxIndex]): Seq[TxIndex] = {

    b.entries.foldLeft(Seq[TxIndex]())((acc, le) => {
      le.index
      le.ledgerItem.ledgerId
      le.ledgerItem.txIdHexStr
      val e = le.ledgerItem.txEntryBytes.toSignedTxEntry
      e.txId.asHexStr
      val tx = e.txEntryBytes.toTx
      // are the tx ins in the list of txouts? yes? remove.
      val newOuts: Seq[TxIndex] = tx.ins.flatMap { in =>
        if (isCoinBase(in)) {
          currentOuts :+ in.txIndex
        } else {
          assert(currentOuts.contains(in.txIndex))
          currentOuts.filter(_ == in.txIndex)
        }
      }
      // add the tx outs to the list
      val plusNewOuts = tx.outs.indices.map { i =>
        TxIndex(tx.txId, i)
      }

      acc ++ plusNewOuts ++ newOuts
    })
  }
}

class OrchestratingActor(clientNode: ClientNode) extends Actor {
  import clientNode._

  private case object ConnectHome


    override def receive: Receive = {
      case StateMachineInitialised =>
        startNetwork
        context.system.scheduler.scheduleOnce(
          FiniteDuration(5, SECONDS),
          self, ConnectHome)

      case ConnectHome => connectHome

      case ReadyStateEvent =>
        log.info("Ready")
        var outs:Seq[TxIndex] = Seq()

          for(i <- 0l to bc.lastBlockHeader.height) {
            log.info(s"Attempting Block Height $i")
            val b = Block(i)
            assert(b.height == i)
            outs = Analysis.analyse(b, outs)

          }


    }


  }