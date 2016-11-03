package sss.analysis


import akka.actor.{Actor, ActorRef, Props}
import sss.analysis.AnalysisOld.Accumulator

import sss.asado.balanceledger.{TxIndex, _}
import sss.asado.block.Block

import sss.asado.nodebuilder.MinimumNode


/**
  * Created by alan on 8/17/16.
  */
object MainCmd {

  object NullActor extends Actor {

    override def receive: Receive = {
      case x => println(s"NULL ACTOR $x")
    }
  }

  def main(args: Array[String]): Unit = {

    new MinimumNode {
      override val configName: String = args(0)
      override val phrase: Option[String] = Option(args(1))
      override val stateMachineActor: ActorRef = {
        actorSystem.actorOf(Props(NullActor))
      }

      log.info("Ready")
      var outs:Accumulator = Accumulator(0,Seq(), Map().withDefaultValue(Seq()))
      val all = bc.lastBlockHeader.height

      for(i <- 1l to all) {

        val b = Block(i)
        log.info(s"Attempting Block Height $i of $all with ${b.entries.size} txs.")
        assert(b.height == i)
        val cbIn = outs.coinbaseTotal
        outs = AnalysisOld.analyse(b, outs)
        val newCb = outs.coinbaseTotal
        val cbInc = newCb - cbIn
        if(cbInc > 1000) {
          log.info(s"Coinbase increase is $cbInc ")
        }
        log.info(s"Outs size now -> ${outs.currentOuts.size}, coinbase increase is ${cbInc} to $newCb")
      }

      var finalBalance = outs.currentOuts.foldLeft(0)((acc, e) => { acc + e.txOut.amount })
      val ledgerBalance = balanceLedger.balance
      val badBalance = finalBalance - ledgerBalance
      log.info(s"Blocks analysis gives $finalBalance, ledger balance is $ledgerBalance, missing is $badBalance ")
      log.info("Done Analysing Blocks")

      balanceLedger.keys.map { txInd =>
        if(outs.currentOuts.find(_.txIndex == txInd).isEmpty) println(s"Could not find $txInd in gathered outs")
      }
      var missing = 0
      var found = 0
      var matching = 0
      outs.currentOuts.foreach { io =>
        balanceLedger.entry(io.txIndex) match {
          case None =>
            log.info(s"Why is my TxIndx ${io.txIndex} not in the ledger?")
            missing += 1
          case Some(txOut) =>
            found += 1
            if(io.txOut != txOut) {
              log.info(s"${io.txOut} != $txOut")
            } else matching += 1
        }
      }
      log.info(s"Num missing $missing, num found $found, numb amounts matching $matching")
      actorSystem.terminate
    }
  }
}


