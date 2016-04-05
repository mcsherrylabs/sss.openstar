package sss.asado.storage

import ledger._
import sss.asado.util.ByteArrayVarcharOps._
import sss.db.{Db, Where}

class UTXODBStorage(implicit db: Db) extends Storage[TxIndex, TxOutput] {

  private val utxoLedgerTable = db.table("utxo")

  def entries: Set[TxOutput] = {
    utxoLedgerTable.map { row =>
      row[Array[Byte]]("entry").toTxOutput
    }.toSet
  }

  def inTransaction[T](f: => T): T = utxoLedgerTable.inTransaction[T](f)

  def delete(k: TxIndex): Boolean = {
    val hexStr = k.txId.toVarChar
    val numDeleted = utxoLedgerTable.delete(Where("txid = ? AND indx = ?", hexStr, k.index))
    numDeleted == 1
  }

  override def get(k: TxIndex): Option[TxOutput] = {
    val hexStr = k.txId.toVarChar
    utxoLedgerTable.find(Where("txid = ? AND indx = ?", hexStr, k.index)).map(r => r[Array[Byte]]("entry").toTxOutput)
  }

  def write(k: TxIndex, le: TxOutput): Long = {
    val bs = le.toBytes
    val hexStr = k.txId.toVarChar
    val res = utxoLedgerTable.insert(hexStr, k.index, bs)
    res
  }

}
