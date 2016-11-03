package sss.analysis

import sss.asado.balanceledger.{TxInput, TxOutput}
import sss.asado.identityledger.Claim
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.balanceledger.{TxIndex, _}
import sss.asado.block.Block
import sss.asado.identityledger._
import sss.asado.ledger._

import sss.asado.util.ByteArrayEncodedStrOps._
object AnalysisOld extends Logging {


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


