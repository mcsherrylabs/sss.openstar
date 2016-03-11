package sss.asado.ledger

import ledger.{TxIndex, GenisesTx, TxId, SignedTx}
import sss.ancillary.Logging
import sss.asado.storage.Storage


class Ledger(val storage: Storage[TxId, SignedTx]) extends Logging {

  def genesis(genisesTx: GenisesTx) = storage.write(SignedTx(genisesTx))

  def exists(txIndex: TxIndex): Boolean = {
    val stxs = storage.entries
    val stxExists = stxs find( tx => tx.txId == txIndex.txId)
    stxExists match {
      case None => false
      case Some(stx) => txIndex.index < stx.tx.outs.length
    }
  }

  def utxos: Set[TxIndex] = {

    val stxs = storage.entries
    stxs.flatMap { stx =>
      stx.tx.outs.foldLeft(Seq[TxIndex]())((acc, out) =>
        acc :+ TxIndex(stx.txId, acc.size)
      )
    }
  }

  def entry(id: TxId): SignedTx = storage(id)

  def isUnspent(txIndx: TxIndex): Boolean = {
    val txs = storage.entries

    def hasTxIndexAsInput(l: SignedTx): Boolean = {
      l.tx.ins.find(in => in.txIndex == txIndx) match {
        case Some(found) => true
        case None => false
      }
    }

    txs find { le => hasTxIndexAsInput(le) } match {
      case Some(t) => false
      case None => true
    }
  }

  def apply(stx: SignedTx) {

    import stx.tx._
    require(ins.length > 0, "Tx has no ins")
    require(outs.length > 0, "Tx has no outs")

    log.debug("Tx has at least 1 in and 1 out")

    var totalIn = 0
    ins foreach { in =>
      entry(in.txIndex.txId) match {
        case l: SignedTx => {
          require(l.tx.outs(in.txIndex.index).amount >= in.amount)
          totalIn += in.amount
          require(exists(in.txIndex))
          require(isUnspent(in.txIndex))
          log.debug(s"${in.txIndex} is unspent")
          require(l.tx.outs(in.txIndex.index).encumbrance.decumber(txId +: stx.params, in.sig))
        }
      }
    }

    log.debug(s"Tx total in amount = $totalIn")

    var totalOut = 0
    outs foreach { out =>
      require(out.amount >= 0)
      totalOut += out.amount
    }

    log.debug(s"Tx total out amount = $totalOut")
    require(totalOut <= totalIn, "Total out *must* be less than or equal to total in")

    storage.write(stx)

  }

}

