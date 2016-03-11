package sss.asado.storage

import ledger._

class MemoryStorage(gensis: SignedTx) extends Storage[TxId, SignedTx] {

  def entries: Set[SignedTx] = ledgerImpl.values.toSet
  def get(id: TxId): Option[SignedTx] = ledgerImpl.get(id)
  def write(le: SignedTx) = ledgerImpl += le.txId -> le

  private var ledgerImpl = Map[TxId, SignedTx]()
  ledgerImpl += (gensis.txId -> gensis)

}
