package sss.asado.account

import scorex.crypto.signatures.Curve25519
import scorex.crypto.signatures.SigningFunctions._
import sss.asado.crypto.SeedBytes

/**
  * Binds a private key to public key and provides signing of messages.
  *
  * @param privateKey
  * @param publicKey
  */
case class PrivateKeyAccount(privateKey: Array[Byte],
                             override val publicKey: Array[Byte])
  extends PublicKeyAccount(publicKey) {
  override val address = Account.fromPubkey(publicKey)
  def sign(msg : MessageToSign): Signature = Curve25519.sign(privateKey, msg)
  def getSharedSecret(othersPublicKey: Array[Byte]) = Curve25519.createSharedSecret(privateKey, othersPublicKey)
}

object PrivateKeyAccount {
  def apply(keyPair: (Array[Byte], Array[Byte])):PrivateKeyAccount = PrivateKeyAccount(keyPair._1, keyPair._2)
  def apply(seed: Array[Byte]):PrivateKeyAccount = apply(Curve25519.createKeyPair(seed))
  def apply(seedBytes:SeedBytes):PrivateKeyAccount = {
    val seed = seedBytes.strongSeed(32)
    apply(Curve25519.createKeyPair(seed))
  }
}
