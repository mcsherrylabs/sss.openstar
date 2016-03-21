package sss.asado.ledger

import javax.xml.bind.DatatypeConverter

import ledger.{GenisesTx, SignedTx, TxIndex, TxOutput}
import sss.ancillary.Logging
import sss.asado.storage.Storage


class UTXOLedger(val storage: Storage[TxIndex, TxOutput]) extends Logging {

  def genesis(genisesTx: GenisesTx) = {
    val stx = SignedTx(genisesTx)
    stx.tx.outs.foldLeft(0){ (acc, out) =>
      storage.write(TxIndex(stx.txId, acc), out)
      acc + 1
    }
  }

  def entry(inIndex: TxIndex): Option[TxOutput] = storage.get(inIndex)

  def apply(stx: SignedTx) {

    storage.inTransaction {
      import stx.tx._
      require(ins.length > 0, "Tx has no ins")
      require(outs.length > 0, "Tx has no outs")

      log.debug("Tx has at least 1 in and 1 out")

      var totalIn = 0

      ins foreach { in =>
        entry(in.txIndex) match {
          case Some(txOut) => {
            require(txOut.amount >= in.amount)
            totalIn += in.amount
            val asStr = DatatypeConverter.printHexBinary(in.txIndex.txId)
            log.debug(s"${asStr}, ${in.txIndex.index} is unspent and a valid amount (${in.amount}).")
            require(txOut.encumbrance.decumber(txId +: stx.params, in.sig))
            storage.delete(in.txIndex)
          }
          case None => throw new IllegalArgumentException(s"${in.txIndex} does not exist.")
        }
      }

      log.debug(s"Tx total in amount = $totalIn")

      var totalOut = 0
      outs.foldLeft(0) { (acc, out) =>
        require(out.amount >= 0)
        totalOut += out.amount
        storage.write(TxIndex(txId, acc), out)
        acc + 1
      }

      log.debug(s"Tx total out amount = $totalOut")
      require(totalOut <= totalIn, "Total out *must* be less than or equal to total in")

    }
  }

}

