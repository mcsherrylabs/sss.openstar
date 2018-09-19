package sss.asado.balanceledger

import sss.ancillary.Logging
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.util.StringCheck.SimpleTag
import sss.db._

private[balanceledger] class UTXODBStorage(tag:SimpleTag)(implicit db: Db) extends Logging {

  private val tableName = s"utxo_$tag"

  private val createTableSql =
    s"""CREATE TABLE IF NOT EXISTS
        |$tableName
        |(txid VARCHAR(64) NOT NULL,
        |indx INT NOT NULL,
        |entry BLOB NOT NULL,
        |PRIMARY KEY (txid, indx));
        |""".stripMargin

  db.executeSql(createTableSql)

  private val utxoLedgerTable = db.table(tableName)

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
