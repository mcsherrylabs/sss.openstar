package sss.asado.storage

import ledger._

class MemoryStorage(gensis: SignedTx) extends Storage[TxIndex, TxOutput] {

  def entries: Set[TxOutput] = ledgerImpl.values.toSet
  def get(id: TxIndex): Option[TxOutput] = ledgerImpl.get(id)
  def write(id: TxIndex, le: TxOutput) = {
    ledgerImpl += id -> le
    0
  }

  override def delete(k: TxIndex): Boolean = { ledgerImpl -= k; true }

  private var ledgerImpl = Map[TxIndex, TxOutput]()

  gensis.tx.outs.foldLeft(0){ (acc, out) =>
    ledgerImpl += (TxIndex(gensis.txId, acc) -> out)
    acc + 1
  }

  override def inTransaction[T](f: => T): T = f
}
