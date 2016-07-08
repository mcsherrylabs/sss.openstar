package sss.asado.account

import scorex.crypto.signatures.Curve25519
import scorex.crypto.signatures.SigningFunctions.{MessageToSign, Signature}


object PublicKeyAccount {
  def apply(publicKey: Array[Byte]) = new PublicKeyAccount(publicKey)
}

class PublicKeyAccount(val publicKey: Array[Byte]) extends Account(Account.fromPubkey(publicKey)) {
  def verify(sig: Signature, msg: MessageToSign) = Curve25519.verify(sig, msg, publicKey)
}
