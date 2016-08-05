package sss.asado.contract

import java.nio.charset.StandardCharsets

import sss.asado.contract.SaleOrReturnSecretEnc.HashedSecret
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.identityledger.IdentityService
import sss.asado.ledger.TxId
import sss.asado.util.hash.SecureCryptographicHash

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 2/16/16.
  */

object SaleOrReturnSecretEnc {

  case class HashedSecret(bytes: Array[Byte])

  def hashSecret(secret: String): HashedSecret = HashedSecret(SecureCryptographicHash.hash(secret))
  def hashSecret(secret: Array[Byte]): HashedSecret = HashedSecret(SecureCryptographicHash.hash(secret))

  def apply(returnIdentity: String,
            claimant: String,
            secret: String,
            returnBlockHeight: Long): SaleOrReturnSecretEnc =
    new SaleOrReturnSecretEnc(returnIdentity,claimant, hashSecret(secret), returnBlockHeight)

  def apply(returnIdentity: String,
            claimant: String,
            secret: Array[Byte],
            returnBlockHeight: Long): SaleOrReturnSecretEnc =
    new SaleOrReturnSecretEnc(returnIdentity,claimant, hashSecret(secret), returnBlockHeight)

}

case class SaleOrReturnSecretEnc(
                                  returnIdentity: String,
                                  claimant: String,
                                  hashOfSecret: HashedSecret,
                                  returnBlockHeight: Long
                                ) extends Encumbrance with ByteArrayComparisonOps {

  override def toString: String = s"SaleOrReturnSecretEnc for claimant $claimant from $returnIdentity after $returnBlockHeight"

  def decumber(params: Seq[Array[Byte]], context: LedgerContext, decumbrance: Decumbrance): Boolean = {

    val currentBlockHeight: Long = context.blockHeight.get
    val identityService: IdentityService =
      context.identityService.getOrElse(throw new Error("Identity service not available"))

    decumbrance match {
      case ReturnSecretDec =>
        val msg = params(0)
        val sig = params(1)
        val tag = new String(params(2), StandardCharsets.UTF_8)
        require(currentBlockHeight >= returnBlockHeight, s"$currentBlockHeight < $returnBlockHeight, cannot reclaim this yet.")
        identityService.accountOpt(returnIdentity, tag) match {
          case None => throw new IllegalArgumentException(s"Cannot find identity $returnIdentity")
          case Some(account) => account.verify(sig, msg)
        }

      case SaleSecretDec =>
        val msg = params(0)
        val sig = params(1)
        val tag = new String(params(2), StandardCharsets.UTF_8)
        val secret = params(3)
        identityService.accountOpt(claimant, tag) match {
          case None => throw new IllegalArgumentException(s"Cannot find identity $returnIdentity")
          case Some(account) => require(account.verify(sig, msg), s"Signature did not match")
        }
        SecureCryptographicHash.hash(secret) isSame hashOfSecret.bytes

      case _ => false
    }
  }

  override def hashCode(): Int = returnBlockHeight.hashCode() +
    returnIdentity.hashCode +
    claimant.hashCode +
    hashOfSecret.bytes.hash

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case that: SaleOrReturnSecretEnc =>
        that.returnIdentity == returnIdentity &&
        that.claimant == claimant &&
        that.returnBlockHeight == returnBlockHeight &&
          that.hashOfSecret.bytes.isSame(hashOfSecret.bytes)

      case _ => false
    }
  }
}

case object SaleSecretDec extends Decumbrance {
  /**
    * Utility method to make generating signature sequences more organised
    */
  def createUnlockingSignature(txId:TxId, tag:String,
                               signer: (Array[Byte]) => Array[Byte],
                               secret: Array[Byte]): Seq[Array[Byte]] = {

    Seq(signer(txId), tag.getBytes, secret)
  }
}

case object ReturnSecretDec extends Decumbrance {
  /**
    * Utility method to make generating signature sequences more organised
    */
  def createUnlockingSignature(txId:TxId, tag:String, signer: (Array[Byte]) => Array[Byte]): Seq[Array[Byte]] = {
    Seq(signer(txId), tag.getBytes)
  }
}