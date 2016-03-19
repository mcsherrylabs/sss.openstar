package sss.asado.storage

import ledger._
import sss.db.{Db, Where}


class TxDBStorage(tableName: String)(implicit db:Db) extends Storage[TxId, SignedTx] {

  private val blockTxTable= db.table(tableName)

  override def entries: Set[SignedTx] = {
    blockTxTable.map { row =>
      row[Array[Byte]]("entry").toSignedTx
    }.toSet
  }

  override def inTransaction(f: => Unit): Unit = blockTxTable.inTransaction[Unit](f)

  override def get(id: TxId): Option[SignedTx] = blockTxTable.find(Where("txid = ?", id)).map(r => r[Array[Byte]]("entry").toSignedTx)

  override def delete(id: TxId): Boolean = blockTxTable.delete(Where("txid = ?", id)) == 1

  override def write(k: TxId, le: SignedTx): Unit = {
    val bs = le.toBytes
    blockTxTable.insert(Map("txid" -> k, "entry" -> bs))
  }

}
