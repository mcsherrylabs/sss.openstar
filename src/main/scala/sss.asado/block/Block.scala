package sss.asado.block

import ledger._
import sss.ancillary.Logging
import sss.asado.util.ByteArrayVarcharOps._
import sss.db.{Db, OrderAsc, Where}

import scala.util.{Failure, Success, Try}


object Block extends Logging {
  private val blockTableNamePrefix = "block_"

  def tableName(height: Long) = s"$blockTableNamePrefix$height"

  def confirm(txId: Array[Byte], height: Long)(implicit db:Db): Unit = {
    Try {
      val blcokTable = db.table(tableName(height))
      val hex = txId.toVarChar
      val rowsUpdated = blcokTable.update("confirm = confirm + 1", s"txid = '$hex'")
      if(rowsUpdated != 1) {
        val blcokTablebehind = db.table(tableName(height - 1))
        val isBehind = blcokTablebehind.find(Where("txid = ?", hex))
        val blcokTableAhead = db.table(tableName(height + 1))
        val isahead = blcokTableAhead.find(Where("txid = ?", hex))
      }
      require(rowsUpdated == 1, s"Must update 1 row, by confirming the tx, not $rowsUpdated rows")

    } match {
      case Failure(e) => log.error(s"FAILED to add confirmation!", e)
      case Success(r) => log.info(s"Tx confirmed. $r")
    }
  }
  def apply(height: Long)(implicit db:Db): Block = new Block(tableName(height))
}

class Block(tableName: String)(implicit db:Db){

  db.executeSql (s"CREATE TABLE IF NOT EXISTS $tableName (txid VARCHAR(64) NOT NULL, entry BLOB, confirm INT DEFAULT 0, PRIMARY KEY (txid))")

  private val blockTxTable = db.table(tableName)

  private[block] def entries: Set[SignedTx] = {
    blockTxTable.map ({ row =>
      row[Array[Byte]]("entry").toSignedTx
    }, OrderAsc("txid")).toSet
  }

  def page(index: Long, pageSize: Int): Seq[Array[Byte]] = {
    require(index < Int.MaxValue, "sss.db 'page' only supports Int, fix to support Long!")
    blockTxTable.page(index.toInt, pageSize, Seq(OrderAsc("txid"))) map(r => r[Array[Byte]]("entry"))
  }

  def apply(k: TxId): SignedTx = get(k).get

  def count = blockTxTable.count

  def inTransaction[T](f: => T): T = blockTxTable.inTransaction[T](f)

  def get(id: TxId): Option[SignedTx] = blockTxTable.find(Where("txid = ?", id.toVarChar)).map(r => r[Array[Byte]]("entry").toSignedTx)

  def delete(id: TxId): Boolean = blockTxTable.delete(Where("txid = ?", id.toVarChar)) == 1

  def write(k: TxId, le: SignedTx): Long = {
    val bs = le.toBytes
    val hexStr = k.toVarChar
    blockTxTable.insert(hexStr, bs, 0)
  }


}
