package sss.asado.account

import sss.asado.util.{EllipticCurveCrypto, SeedBytes}

case class PrivateKeyAccount(seed: Array[Byte],
                             privateKey: Array[Byte],
                             override val publicKey: Array[Byte])
  extends PublicKeyAccount(publicKey) {
  override val address = Account.fromPubkey(publicKey)
}

object PrivateKeyAccount {
  def apply(seed: Array[Byte], keyPair: (Array[Byte], Array[Byte])):PrivateKeyAccount = PrivateKeyAccount(seed, keyPair._1, keyPair._2)
  def apply(seed: Array[Byte]):PrivateKeyAccount = apply(seed, EllipticCurveCrypto.createKeyPair(seed))
  def apply():PrivateKeyAccount = {
    val seed = SeedBytes(32)
    apply(seed, EllipticCurveCrypto.createKeyPair(seed))
  }
}
