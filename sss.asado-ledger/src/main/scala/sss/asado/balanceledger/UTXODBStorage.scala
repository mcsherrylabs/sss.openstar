package sss.asado.balanceledger

import sss.asado.balanceledger._
import sss.ancillary.Logging
import sss.asado.util.ByteArrayVarcharOps._
import sss.db.{Db, Where}

private[balanceledger] class UTXODBStorage(implicit db: Db) extends Logging {

  private val utxoLedgerTable = db.table("utxo")

  def entries: Set[TxOutput] = {
    utxoLedgerTable.map { row =>
      row[Array[Byte]]("entry").toTxOutput
    }.toSet
  }

  def apply(k: TxIndex): TxOutput = get(k).get

  def inTransaction[T](f: => T): T = utxoLedgerTable.inTransaction[T](f)

  def delete(k: TxIndex): Boolean = {
    val hexStr = k.txId.toVarChar
    val numDeleted = utxoLedgerTable.delete(Where("txid = ? AND indx = ?", hexStr, k.index))
    numDeleted == 1
  }

  def get(k: TxIndex): Option[TxOutput] = {
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
