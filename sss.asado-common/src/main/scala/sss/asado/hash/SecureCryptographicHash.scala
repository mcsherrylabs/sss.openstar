package sss.asado.util.hash

import scorex.crypto._
import scorex.crypto.hash.CryptographicHash._
import scorex.crypto.hash.{Blake256, CryptographicHash, Keccak256}


/**
  * The chain of two hash functions, Blake and Keccak
  */

object SecureCryptographicHash extends CryptographicHash {

  override val DigestSize: Int = 32

  override def hash(in: Message): Digest = applyHashes(in, Blake256, Keccak256)
}
