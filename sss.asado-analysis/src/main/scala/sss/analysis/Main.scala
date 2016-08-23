package sss.analysis


import akka.actor.{Actor, ActorRef, Props}
import sss.analysis.Analysis.{Accumulator, InOut}
import sss.ancillary.Logging
import sss.asado.MessageKeys

import scala.concurrent.ExecutionContext.Implicits.global
import sss.asado.ledger._
import sss.asado.balanceledger.{TxIndex, _}
import sss.asado.identityledger._
import sss.asado.block.Block
import sss.asado.nodebuilder.{ClientNode, MinimumNode}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.state.AsadoStateProtocol.{ReadyStateEvent, StateMachineInitialised}

/**
  * Created by alan on 8/17/16.
  */
object Main {

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
      Analysis.numMissingTxIndexFound = 0
      for(i <- 1l to all) {

        val b = Block(i)
        log.info(s"Attempting Block Height $i of $all with ${b.entries.size} txs.")
        assert(b.height == i)
        val cbIn = outs.coinbaseTotal
        outs = Analysis.analyse(b, outs)
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
      log.info(s"Blocks analysis gives $finalBalance, ledger balance is $ledgerBalance, missing is $badBalance, Analysis.numMissingTxIndexFound = ${Analysis.numMissingTxIndexFound}")
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


object Analysis extends Logging {

  var numMissingTxIndexFound = 0
  val missingTxIndexs = Seq (
    TxIndex("C7F56839E92F4739CA93BB950B5D4EAA70213946ECE4CF03C820A45060157F8F".toByteArray, 0),
    TxIndex("56E358658774D53A7B7F01CB50D00E38510AAD4FD65E42119D2771762CBB4BF6".toByteArray, 0),
    TxIndex("C17D4B2E853BF75FA5BC9B2D43664DFE03B2758827406DBC2D91E00FF118CD71".toByteArray, 0),
    TxIndex("FEBBAFD9891A4DF73889DA51CE703F385DF0CE8B9AF04A939AAE158EBCEB6FC5".toByteArray, 0),
    TxIndex("37F3642941A0524B7D8058323C72A99AAC7F3F270133762522B824AB3DA54969".toByteArray, 0),
    TxIndex("FBD7A40E3E10DC6DDCD0A6024B0B9B1501ED79F7C644974FC3834A7BF34D2E0F".toByteArray, 0),
    TxIndex("FDEF54E2D4175F9D96F48CE4A41E3E8A3685FBC35D9DE85E031B0CAD832E919A".toByteArray, 0),
    TxIndex("FD4176CFC24ECA5F0F77DD11BC60D9B5F70F636A43CE7F1FE46AF22FD79F4E60".toByteArray, 0),
    TxIndex("FAE0D0FC3EB1AABDEFEB22C7A8700093C2D0CF6974E8226E1A7A6F782E80FE5C".toByteArray, 0)
  )

  case class Accumulator(coinbaseTotal: Long, currentOuts: Seq[InOut], wallets: Map[String, Seq[InOut]])
  case class InOut(txIndex: TxIndex, txOut: TxOutput)

  def isCoinBase(input: TxInput): Boolean = {
    input.txIndex.txId sameElements(CoinbaseTxId)
  }

  def analyse(b: Block, currAcc: Accumulator): Accumulator = {

    val coinBasetotalIn = currAcc.coinbaseTotal
    var coinBaseIncrease = 0

    import currAcc.currentOuts
    var balanceIn = currentOuts.foldLeft(0)((acc, e) => { acc + e.txOut.amount })
//    if(b.height > 761) {
//      currentOuts.map(println)
//      println("oh oh")
//    }
    assert(balanceIn == coinBasetotalIn, s"The ledger is unbalanced ${balanceIn} != $coinBasetotalIn.")

    log.info(s"Balance in is now $balanceIn")
    val allEntries = b.entries
    val result: Seq[InOut] = allEntries.foldLeft(currentOuts)((acc, le) => {

        val e = le.ledgerItem.txEntryBytes.toSignedTxEntry
        if (!(e.txId sameElements le.ledgerItem.txId)) {
          println(s"${le.index} ${le.ledgerItem.ledgerId}" +
            s"${le.ledgerItem.txIdHexStr}=${e.txId.toBase64Str} Tx Entry has different txId to LedgerItem!")
        }

        le.ledgerItem.ledgerId match {
          case MessageKeys.IdentityLedger =>
            val msg = e.txEntryBytes.toIdentityLedgerMessage
            assert(msg.txId sameElements le.ledgerItem.txId, "Id ledger txId mismatch")
            msg match {
              case Claim(id, pKey) =>
              case x =>
            }
            acc

          case MessageKeys.BalanceLedger =>

            val tx = e.txEntryBytes.toTx
            // are the tx ins in the list of txouts? yes? remove.
            //var newCoinbases: Seq[InOut] = Seq()

            tx.ins.foreach { in =>
              if(isCoinBase(in)) {
                assert(tx.outs.head.amount == 1000, s"Coinbase tx is not 1000, ${tx.outs.head.amount}")
                assert(tx.outs.size == 1, s"Coinbase tx has more than one output, ${tx.outs.size}")
                coinBaseIncrease = coinBaseIncrease + tx.outs.head.amount
                //newCoinbases = newCoinbases :+ InOut(TxIndex(tx.txId, 0), tx.outs.head)
              } else {
                assert(acc.find(_.txIndex == in.txIndex).isDefined, s"TxIndex from nowhere ${in.txIndex}")
              }
            }

            val newOuts = acc.filterNot(index => tx.ins.find(_.txIndex == index.txIndex).isDefined)
            // add the tx outs to the list
            val plusNewOuts = tx.outs.indices.map { i =>
              //assert(tx.outs(i).amount > 0, "Why txOut is 0?")<-because the server charge can be 0
              val newIndx = TxIndex(tx.txId, i)
              if(missingTxIndexs.find(_ == newIndx).isDefined) {
                log.info(s"$newIndx created in block ${b.height}, row ${le.index}")
                numMissingTxIndexFound += 1
              }
              InOut(newIndx,tx.outs(i))
            }

            //if(b.height > 47) println("> 47")
            plusNewOuts ++ newOuts
          case x =>
            println(s"Another type of ledger? $x")
            acc
        }

      })

    Accumulator(coinBasetotalIn + coinBaseIncrease, result, Map())
  }
}

