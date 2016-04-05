
import javax.xml.bind.DatatypeConverter

import contract.{Decumbrance, Encumbrance}
import sss.asado.account.PrivateKeyAccount
import sss.asado.hash.SecureCryptographicHash
import sss.asado.ledger.serialize._
import sss.asado.util.Serialize._
import sss.asado.util.{ByteArrayComparisonOps, EllipticCurveCrypto}

import scala.util.Try

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
package object ledger extends ByteArrayComparisonOps {


  type TxId = Array[Byte]
  val TxIdLen = 32
  val TxIndexLen = TxIdLen + 4
  case class TxIndex(txId: TxId, index: Int) {
    override def equals(obj: scala.Any): Boolean = obj match {
      case txIndx: TxIndex => txIndx.index == index && txIndx.txId.isSame(txId)
      case _ => false
    }

    override def hashCode(): Int = (17 + index) * txId.hash

    override def toString : String = DatatypeConverter.printHexBinary(txId) + ":" + index
  }
  case class TxInput(txIndex: TxIndex, amount: Int, sig: Decumbrance)
  case class TxOutput(amount: Int, encumbrance: Encumbrance)
  case class SeqSignedTx(ordered: Seq[SignedTx])

  case class SignedTx(tx: Tx, params: Seq[Array[Byte]] = Seq.empty) {
    lazy val txId = tx.txId

    override def equals(obj: scala.Any): Boolean = obj match {
      case stx: SignedTx => stx.tx == tx && (stx.params isSame params)
      case _ => false
    }
    override def hashCode(): Int = (17 + params.hash) * txId.hash
  }

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

    def sign(pkPair: PrivateKeyAccount) =  {
      EllipticCurveCrypto.sign(pkPair.privateKey, txId)
    }
  }

  case class GenisesTx(id: String = "GENISES1GENISES1GENISES1GENISES1", outs: Seq[TxOutput]) extends Tx {

    val ins: Seq[TxInput] = Seq.empty
    val txId: TxId = id.getBytes

    require(txId.length == TxIdLen)
  }

  case class TxDbId(height: Long)

  implicit class TxIndexTo(t: TxIndex) extends ToBytes[TxIndex] {
    override def toBytes: Array[Byte] = TxIndexSerializer.toBytes(t)
  }
  implicit class TxIndexFrom(b: Array[Byte]) {
     def toTxIndex: TxIndex = TxIndexSerializer.fromBytes(b)
  }

  implicit class TxInputTo(t: TxInput)  extends ToBytes[TxInput] {
    override def toBytes: Array[Byte] = TxInputSerializer.toBytes(t)
  }
  implicit class TxInputFrom(b: Array[Byte]) {
     def toTxInput: TxInput = TxInputSerializer.fromBytes(b)
  }
  implicit class TxOutputTo(t: TxOutput)  extends ToBytes[TxOutput] {
    override def toBytes: Array[Byte] = TxOutputSerializer.toBytes(t)
  }
  implicit class TxOutputFrom(b: Array[Byte]) {
    def toTxOutput: TxOutput = TxOutputSerializer.fromBytes(b)
  }
  implicit class TxTo(t: Tx)  extends ToBytes[Tx] {
    override def toBytes: Array[Byte] = TxSerializer.toBytes(t)
  }
  implicit class TxFrom(b: Array[Byte]) {
    def toTx: Tx = TxSerializer.fromBytes(b)
  }
  implicit class SignedTxTo(t: SignedTx)  extends ToBytes[SignedTx] {
    override def toBytes: Array[Byte] = SignedTxSerializer.toBytes(t)
  }
  implicit class SignedTxFrom(b: Array[Byte]) {
    def toSignedTx: SignedTx = SignedTxSerializer.fromBytes(b)
    def toSignedTxTry = Try(toSignedTx)
  }
  implicit class SeqSignedTxTo(t: SeqSignedTx)  extends ToBytes[SeqSignedTx] {
    override def toBytes: Array[Byte] = SeqSignedTxSerializer.toBytes(t)
  }
  implicit class SeqSignedTxFrom(b: Array[Byte]) {
    def toSeqSignedTx: SeqSignedTx = SeqSignedTxSerializer.fromBytes(b)
    def toSeqSignedTxTry = Try(toSeqSignedTx)
  }


}
