package sss.asado.ledger

import ledger.{SignedTx, TxDbId, TxId}
import sss.ancillary.Logging
import sss.asado.storage.Storage

import scala.util.Try


class TxInLedger(txId: TxId) extends RuntimeException("Tx already in ledger")

class Ledger(val blockHeight: Long, storage: Storage[TxId, SignedTx], utxo: UTXOLedger) extends Logging {

  def apply(stx: SignedTx): Try[TxDbId] = {

    Try {

      storage.inTransaction[Option[TxDbId]] {
        storage.get(stx.txId) match {
          case Some(s) => None
          case None =>
            utxo(stx)
            val id = storage.write(stx.txId, stx)
            Some(TxDbId(blockHeight, id))
        }
      } match {
        case None => throw new TxInLedger(stx.txId)
        case Some(x) => x
      }
    }
  }

}

