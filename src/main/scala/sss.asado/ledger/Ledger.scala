package sss.asado.ledger

import javax.xml.bind.DatatypeConverter

import ledger.{GenisesTx, SignedTx, TxIndex, TxOutput}
import sss.ancillary.Logging
import sss.db.Db

object Ledger {
  def apply()(implicit db:Db) : Ledger = new Ledger(new UTXODBStorage())
}

class Ledger(storage: UTXODBStorage) extends Logging {

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
      require(ins.nonEmpty, "Tx has no inputs")
      require(outs.nonEmpty, "Tx has no outputs")

      var totalIn = 0

      ins foreach { in =>
        entry(in.txIndex) match {
          case Some(txOut) => {
            require(txOut.amount >= in.amount, s"Cannot pay out (${in.amount}), only ${txOut.amount} available ")
            totalIn += in.amount
            val asStr = DatatypeConverter.printHexBinary(in.txIndex.txId)
            require(txOut.encumbrance.decumber(txId +: stx.params, in.sig), "Failed to decumber!")
            storage.delete(in.txIndex)
          }
          case None => throw new IllegalArgumentException(s"${in.txIndex} does not exist.")
        }
      }

      var totalOut = 0
      outs.foldLeft(0) { (acc, out) =>
        require(out.amount >= 0, "Out amount *must* be greater than 0")
        totalOut += out.amount
        storage.write(TxIndex(txId, acc), out)
        acc + 1
      }

      require(totalOut <= totalIn, "Total out *must* be less than or equal to total in")

    }
  }

}

