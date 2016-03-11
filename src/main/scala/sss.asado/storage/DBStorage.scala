package sss.asado.storage

import ledger._
import sss.db.{Db, Where}


class DBStorage(ledgerDbConfigName: String) extends Storage[TxId, SignedTx] {

  private val ledgerTable = Db(ledgerDbConfigName).table("ledger")

  def entries: Set[SignedTx] = {
    ledgerTable.map { row =>
      row[Array[Byte]]("entry").toSignedTx
    }.toSet
  }

  def get(id: TxId): Option[SignedTx] = ledgerTable.find(Where("txid = ?", id.array)).map(r => r[Array[Byte]]("entry").toSignedTx)

  def write(le: SignedTx): Unit = {
    val bs = le.toBytes
    ledgerTable.insert(Map("txid" -> le.tx.txId.array, "entry" -> bs))
  }

}
