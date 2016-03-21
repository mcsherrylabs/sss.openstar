package sss.asado.ledger

import ledger.{SignedTx, TxId}
import sss.ancillary.Logging
import sss.asado.storage.Storage

import scala.util.Try


class Ledger(val blockHeight: Long, storage: Storage[TxId, SignedTx], utxo: UTXOLedger) extends Logging {

  def apply(stx: SignedTx): Try[Long] = {

    Try {
      storage.inTransaction {
        utxo(stx)
        storage.write(stx.txId, stx)
      }
      blockHeight
    }
  }

}

