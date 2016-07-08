package sss.asado.ledger

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.crypto.SeedBytes


class SignedTxEntrySpec extends FlatSpec with Matchers {

  val randomTxEntryBytes1 = SeedBytes(345)
  val randomTxEntryBytes2 = SeedBytes(35)
  val randomTxEntryBytes3 = SeedBytes(5)

  val randomSigBytes1 = SeedBytes(35)
  val randomSigBytes2 = SeedBytes(5)

  val stx = SignedTxEntry(randomTxEntryBytes1, Seq(Seq(randomSigBytes1)))
  val stx2 = SignedTxEntry(randomTxEntryBytes1, Seq(Seq(randomSigBytes1), Seq(randomSigBytes2)))
  val otherStxWithoutSig = SignedTxEntry(stx.toBytes)

  "A Signed Tx Entry " should " be parseable to bytes " in {
    val bytes: Array[Byte] = stx.toBytes

  }

  it should " be parseable from bytes to an equal instance " in {
    val bytes: Array[Byte] = stx.toBytes
    val backAgain = bytes.toSignedTxEntry
    assert(backAgain === stx)
    assert(backAgain.hashCode() === stx.hashCode)
  }

  it should " be not equal to a different instance " in {
    assert(stx !== otherStxWithoutSig)
    assert(stx.hashCode !== otherStxWithoutSig.hashCode)
  }

  it should " match the sig to the inputs after deserialization " in {
    val bytes: Array[Byte] = stx2.toBytes
    val backAgain = bytes.toSignedTxEntry
    assert(backAgain === stx2)
    assert(backAgain.signatures(0)(0) === stx2.signatures(0)(0))
    assert(backAgain.signatures(1)(0) === stx2.signatures(1)(0))
    assert(backAgain.signatures(1)(0) !== stx2.signatures(0)(0))
  }
}
