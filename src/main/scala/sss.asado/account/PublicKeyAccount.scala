package sss.asado.account

import scorex.crypto.signatures.SigningFunctions.{MessageToSign, Signature}
import sss.asado.util.EllipticCurveCrypto

object PublicKeyAccount {
  def apply(publicKey: Array[Byte]) = new PublicKeyAccount(publicKey)
}

class PublicKeyAccount(val publicKey: Array[Byte]) extends Account(Account.fromPubkey(publicKey)) {
  def verify(sig: Signature, msg: MessageToSign) = EllipticCurveCrypto.verify(sig, msg, publicKey)
}
