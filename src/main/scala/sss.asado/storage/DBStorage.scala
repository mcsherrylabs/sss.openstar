package sss.asado.storage

import ledger._
import sss.db.{Db, Where}


class DBStorage(ledgerDbConfigName: String) extends Storage[TxId, SignedTx] {

  private val ledgerTable = Db(ledgerDbConfigName).table("ledger")

  override def entries: Set[SignedTx] = {
    ledgerTable.map { row =>
      row[Array[Byte]]("entry").toSignedTx
    }.toSet
  }

  override def inTransaction(f: => Unit): Unit = ledgerTable.inTransaction[Unit] _

  override def get(id: TxId): Option[SignedTx] = ledgerTable.find(Where("txid = ?", id)).map(r => r[Array[Byte]]("entry").toSignedTx)

  override def delete(id: TxId): Boolean = ledgerTable.delete(Where("txid = ?", id)) == 1

  override def write(k: TxId, le: SignedTx): Unit = {
    val bs = le.toBytes
    ledgerTable.insert(Map("txid" -> k, "entry" -> bs))
  }

}
