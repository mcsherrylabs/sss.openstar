package sss.asado.storage

import ledger._
import sss.ancillary.Logging
import sss.asado.util.ByteArrayVarcharOps._
import sss.db.{Db, OrderAsc, Where}

import scala.util.{Failure, Success, Try}


object TxDBStorage extends Logging {
  private val blockTableNamePrefix = "block_"

  def tableName(height: Long) = s"$blockTableNamePrefix$height"

  def confirm(txId: Array[Byte], height: Long)(implicit db:Db): Unit = {
    Try {
      val blcokTable = db.table(tableName(height))
      val hex = txId.toVarChar
      val rowsUpdated = blcokTable.update("confirm = confirm + 1", s"txid = $hex")
      require(rowsUpdated == 1)

    } match {
      case Failure(e) => log.error(s"FAILED to add confirmation!", e)
      case Success(r) => log.info(s"Tx confirmed. $r")
    }
  }
  def apply(height: Long)(implicit db:Db): TxDBStorage = new TxDBStorage(tableName(height))
}

class TxDBStorage(tableName: String)(implicit db:Db) extends Storage[TxId, SignedTx] {

  db.executeSql (s"CREATE TABLE IF NOT EXISTS $tableName (txid VARCHAR(64) NOT NULL, entry BLOB, confirm INT DEFAULT 0, PRIMARY KEY (txid))")

  private val blockTxTable = db.table(tableName)

  private[storage] override def entries: Set[SignedTx] = {
    blockTxTable.map ({ row =>
      row[Array[Byte]]("entry").toSignedTx
    }, OrderAsc("txid")).toSet
  }

  def page(index: Long, pageSize: Int): Seq[Array[Byte]] = {
    require(index < Int.MaxValue, "sss.db 'page' only supports Int, fix to support Long!")
    blockTxTable.page(index.toInt, pageSize, Seq(OrderAsc("txid"))) map(r => r[Array[Byte]]("entry"))
  }

  def count = blockTxTable.count

  override def inTransaction[T](f: => T): T = blockTxTable.inTransaction[T](f)

  override def get(id: TxId): Option[SignedTx] = blockTxTable.find(Where("txid = ?", id.toVarChar)).map(r => r[Array[Byte]]("entry").toSignedTx)

  override def delete(id: TxId): Boolean = blockTxTable.delete(Where("txid = ?", id.toVarChar)) == 1

  override def write(k: TxId, le: SignedTx): Long = {
    val bs = le.toBytes
    val hexStr = k.toVarChar
    blockTxTable.insert(hexStr, bs, 0)
  }


}
