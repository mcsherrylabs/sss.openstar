package sss.asado.ledger

import ledger.{GenisesTx, SignedTx, TxDbId, TxId}
import sss.ancillary.Logging
import sss.asado.storage.Storage

import scala.util.Try


case class TxInLedger(txId: TxId) extends RuntimeException("Tx already in ledger")

class Ledger(val blockHeight: Long, storage: Storage[TxId, SignedTx], utxo: UTXOLedger) extends Logging {

  def genesis(genesisTx: GenisesTx): Try[TxDbId] = {
    Try {
      storage.inTransaction[Option[TxDbId]] {
        storage.get(genesisTx.txId) match {
          case Some(s) => None
          case None =>
            utxo.genesis(genesisTx)
            storage.write(genesisTx.txId, SignedTx(genesisTx))
            Some(TxDbId(blockHeight))
        }
      } match {
        case None => throw new TxInLedger(genesisTx.txId)
        case Some(x) => x
      }
    }
  }
  def apply(stx: SignedTx): Try[TxDbId] = {

    Try {
      storage.inTransaction[Option[TxDbId]] {
        storage.get(stx.txId) match {
          case Some(s) => None
          case None =>
            utxo(stx)
            val id = storage.write(stx.txId, stx)
            Some(TxDbId(blockHeight))
        }
      } match {
        case None => throw TxInLedger(stx.txId)
        case Some(x) => x
      }
    }
  }

}

