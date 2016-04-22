package sss.asado.account

import scorex.crypto.signatures.SigningFunctions._
import sss.asado.util.{EllipticCurveCrypto, SeedBytes}

case class PrivateKeyAccount(
                             privateKey: Array[Byte],
                             override val publicKey: Array[Byte])
  extends PublicKeyAccount(publicKey) {
  override val address = Account.fromPubkey(publicKey)
  def sign(msg : MessageToSign): Signature = EllipticCurveCrypto.sign(privateKey, msg)
}

object PrivateKeyAccount {
  def apply(keyPair: (Array[Byte], Array[Byte])):PrivateKeyAccount = PrivateKeyAccount(keyPair._1, keyPair._2)
  def apply(seed: Array[Byte]):PrivateKeyAccount = apply(EllipticCurveCrypto.createKeyPair(seed))
  def apply():PrivateKeyAccount = {
    val seed = SeedBytes(32)
    apply(EllipticCurveCrypto.createKeyPair(seed))
  }
}
