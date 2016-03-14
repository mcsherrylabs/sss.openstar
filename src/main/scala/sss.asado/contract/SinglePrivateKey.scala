package sss.asado.contract

import contract.{Decumbrance, Encumbrance}
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.EllipticCurveCrypto

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */
case class SinglePrivateKey(pKey: PublicKey) extends Encumbrance with ByteArrayComparisonOps {


  override def equals(obj: scala.Any): Boolean = obj match {
    case spk: SinglePrivateKey => spk.pKey.isSame(pKey)
    case _ => false
  }


  override def hashCode(): Int = pKey.hash

  def decumber(params: Seq[Array[Byte]], decumbrance: Decumbrance): Boolean = {
    decumbrance match {
      case PrivateKeySig => {
        val msg = params(0)
        val sig = params(1)
        val r = EllipticCurveCrypto.verify(sig, msg, pKey)
        r
      }
      case _ => false
    }
  }
}

case object PrivateKeySig extends Decumbrance
