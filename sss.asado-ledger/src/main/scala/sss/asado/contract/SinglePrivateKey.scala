package sss.asado.contract

import scorex.crypto.signatures.Curve25519
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.ledger._
import sss.asado.util.ByteArrayComparisonOps


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */
case class SinglePrivateKey(pKey: PublicKey, minBlockHeight: Long = 0) extends Encumbrance with ByteArrayComparisonOps {

  override def equals(obj: scala.Any): Boolean = obj match {
    case spk: SinglePrivateKey => spk.pKey.isSame(pKey) && minBlockHeight == spk.minBlockHeight
    case _ => false
  }

  override def hashCode(): Int = pKey.hash

  def decumber(params: Seq[Array[Byte]], context: LedgerContext, decumbrance: Decumbrance): Boolean = {

    val currentBlockHeight: Long = context.blockHeight.get

    decumbrance match {
      case PrivateKeySig => {
        val msg = params(0)
        val sig = params(1)
        val r = Curve25519.verify(sig, msg, pKey)
        require(currentBlockHeight >= minBlockHeight, s"$currentBlockHeight < $minBlockHeight, cannot spend this yet.")
        r
      }
      case _ => false
    }
  }
}

case object PrivateKeySig extends Decumbrance {
  def createUnlockingSignature(signatureOfTxId: Array[Byte]): Seq[Array[Byte]] = {
    Seq(signatureOfTxId)
  }
}
