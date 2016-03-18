package sss.asado.storage

import javax.xml.bind.DatatypeConverter

import ledger._
import sss.db.{Db, Where}


class UTXODBStorage(ledgerDbConfigName: String) extends Storage[TxIndex, TxOutput] {

  private val utxoLedgerTable = Db(ledgerDbConfigName).table("utxo")

  def entries: Set[TxOutput] = {
    utxoLedgerTable.map { row =>
      row[Array[Byte]]("entry").toTxOutput
    }.toSet
  }

  def inTransaction(f: => Unit): Unit = utxoLedgerTable.inTransaction[Unit](f)

  def delete(k: TxIndex): Boolean = {
    val hexStr:String = DatatypeConverter.printHexBinary(k.txId)
    val numDeleted = utxoLedgerTable.delete(Where("txid = ? AND indx = ?", hexStr, k.index))
    numDeleted == 1
  }

  override def get(k: TxIndex): Option[TxOutput] = {
    val hexStr: String = DatatypeConverter.printHexBinary(k.txId)
    utxoLedgerTable.find(Where("txid = ? AND indx = ?", hexStr, k.index)).map(r => r[Array[Byte]]("entry").toTxOutput)
  }

  def write(k: TxIndex, le: TxOutput): Unit = {
    val bs = le.toBytes
    val hexStr:String = DatatypeConverter.printHexBinary(k.txId)
    utxoLedgerTable.insert(hexStr, k.index, bs)
  }

}
