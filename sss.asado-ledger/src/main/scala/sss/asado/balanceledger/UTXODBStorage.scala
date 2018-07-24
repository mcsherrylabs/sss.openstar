package sss.asado.balanceledger

import sss.ancillary.Logging
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.db._

private[balanceledger] class UTXODBStorage(implicit db: Db) extends Logging {

  private val utxoLedgerTable = db.table("utxo")

  def keys: Seq[TxIndex] = utxoLedgerTable.map { row => TxIndex(row[String]("txid").toByteArray, row[Int]("indx")) }

  def entries: Seq[TxOutput] = utxoLedgerTable.tx {
    utxoLedgerTable.map { row =>
      row[Array[Byte]]("entry").toTxOutput
    }
  }

  def apply(k: TxIndex): TxOutput = get(k).get

  def inTransaction[T](f: => T): T = utxoLedgerTable.inTransaction[T](f)

  def delete(k: TxIndex): Boolean = {
    val hexStr = k.txId.toBase64Str
    val numDeleted = utxoLedgerTable.delete(where(ps"txid = $hexStr AND indx = ${k.index}"))
    numDeleted == 1
  }

  def get(k: TxIndex): Option[TxOutput] = inTransaction {
    val hexStr = k.txId.toBase64Str
    utxoLedgerTable.find(where(ps"txid = $hexStr AND indx = ${k.index}")).map(r => r[Array[Byte]]("entry").toTxOutput)
  }

  def write(k: TxIndex, le: TxOutput): Long = {
    val bs = le.toBytes
    val hexStr = k.txId.toBase64Str
    val res = utxoLedgerTable.insert(hexStr, k.index, bs)
    res
  }

}
