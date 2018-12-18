package sss.openstar


import sss.openstar.balanceledger.serialize._
import sss.openstar.contract.{Decumbrance, Encumbrance}
import sss.openstar.ledger._
import sss.openstar.util.ByteArrayComparisonOps
import sss.openstar.util.ByteArrayEncodedStrOps._
import sss.openstar.util.Serialize._
import sss.openstar.util.hash.SecureCryptographicHash

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
package object balanceledger extends ByteArrayComparisonOps {

  val TxIndexLen = TxIdLen + 4
  case class TxIndex(txId: TxId, index: Int) {
    override def equals(obj: scala.Any): Boolean = obj match {
      case txIndx: TxIndex => txIndx.index == index && txIndx.txId.isSame(txId)
      case _ => false
    }

    override def hashCode(): Int = (17 + index) * txId.hash

    override def toString : String = txId.toBase64Str + ":" + index
  }
  case class TxInput(txIndex: TxIndex, amount: Int, sig: Decumbrance)
  case class TxOutput(amount: Int, encumbrance: Encumbrance)



  trait Tx {
    val ins: Seq[TxInput]
    val outs: Seq[TxOutput]
    val txId: TxId
  }

  case class StandardTx(ins: Seq[TxInput],  outs: Seq[TxOutput]) extends Tx {
    lazy val txId: TxId = {
      val asBytes= TxSerializer.toBytes(this)
      SecureCryptographicHash.hash(asBytes)
    }
  }

  implicit class TxIndexTo(t: TxIndex) extends ToBytes {
    override def toBytes: Array[Byte] = TxIndexSerializer.toBytes(t)
  }
  implicit class TxIndexFrom(b: Array[Byte]) {
     def toTxIndex: TxIndex = TxIndexSerializer.fromBytes(b)
  }

  implicit class TxInputTo(t: TxInput)  extends ToBytes {
    override def toBytes: Array[Byte] = TxInputSerializer.toBytes(t)
  }
  implicit class TxInputFrom(b: Array[Byte]) {
     def toTxInput: TxInput = TxInputSerializer.fromBytes(b)
  }
  implicit class TxOutputTo(t: TxOutput)  extends ToBytes {
    override def toBytes: Array[Byte] = TxOutputSerializer.toBytes(t)
  }
  implicit class TxOutputFrom(b: Array[Byte]) {
    def toTxOutput: TxOutput = TxOutputSerializer.fromBytes(b)
  }
  implicit class TxTo(t: Tx)  extends ToBytes {
    override def toBytes: Array[Byte] = TxSerializer.toBytes(t)
  }
  implicit class TxFrom(b: Array[Byte]) {
    def toTx: Tx = TxSerializer.fromBytes(b)
  }



}
