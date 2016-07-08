package sss.asado.contract

import java.nio.charset.StandardCharsets
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.identityledger.IdentityService
import sss.asado.ledger.TxId

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */
case class SingleIdentityEnc(identity: String, minBlockHeight: Long = 0) extends Encumbrance with ByteArrayComparisonOps {

  def decumber(params: Seq[Array[Byte]], context: LedgerContext, decumbrance: Decumbrance): Boolean = {

    val currentBlockHeight: Long = context.blockHeight.get
    val identityService: IdentityService =
      context.identityService.getOrElse(throw new Error("Identity service not available"))

    decumbrance match {
      case SingleIdentityDec =>
        val msg = params(0)
        val sig = params(1)
        val tag = new String(params(2), StandardCharsets.UTF_8)
        require(currentBlockHeight >= minBlockHeight, s"$currentBlockHeight < $minBlockHeight, cannot spend this yet.")
        identityService.accountOpt(identity, tag) match {
          case None => throw new IllegalArgumentException(s"Cannot find identity $identity")
          case Some(account) => account.verify(sig, msg)
        }

      case _ => false
    }
  }
}

case object SingleIdentityDec extends Decumbrance {
  /**
    * Utility method to make generating signature sequences more organised
    */
  def createUnlockingSignature(txId:TxId, tag:String, signer: (Array[Byte]) => Array[Byte]): Seq[Array[Byte]] = {
    Seq(signer(txId), tag.getBytes)
  }
}
