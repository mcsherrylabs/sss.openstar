package sss.asado.account

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}
import sss.ancillary.Memento
import sss.asado.util.{ByteArrayComparisonOps, EllipticCurveCrypto}

/**
  * Created by alan on 2/11/16.
  */
class AccountSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  lazy val pkPair = PrivateKeyAccount()

  "An account " should " generate a new public and private key " in {

    assert(Account.isValidAddress(pkPair.address))

  }

  it should " honour equals and hashcode  " in {
    val sut = new Account(pkPair.address)
    assert(sut.address == pkPair.address)

    val sut2 = new Account(pkPair.address)
    assert(sut == sut2)
    assert(sut.hashCode() == sut2.hashCode())

    lazy val pkPair2 = PrivateKeyAccount()
    val sut3 = new Account(pkPair2.address)
    assert(sut != sut3)
    assert(sut != pkPair.address)
  }

  it should "be able to sign a message and verify the mesage was signed " in {

    val msg = "Holy guacamole, it's a message from Zod"
    val wrongMsg = "Hly guacamole, it's a message from Zod"
    val sig = EllipticCurveCrypto.sign(pkPair, msg.getBytes)
    assert(EllipticCurveCrypto.verify(sig, msg.getBytes, pkPair.publicKey))
    assert(!EllipticCurveCrypto.verify(sig, wrongMsg.getBytes, pkPair.publicKey))

  }


  it should " consistently generate addresses from public keys (even if addresses are never used)" in {

    val addr = pkPair.address
    assert(Account.isValidAddress(addr))

    val address = Account.fromPubkey(pkPair.publicKey)
    assert(Account.isValidAddress(address))

    val pkAgain = new PublicKeyAccount(pkPair.publicKey)
    assert(address === Account.fromPubkey(pkAgain.publicKey))
  }

  "A client key " should " provide a unique new pkPair " in {

    val tagA = UUID.randomUUID().toString
    val tagB = UUID.randomUUID().toString
    try {

      val a = ClientKey(tagA)
      val b = ClientKey(tagB)

      assert(!a.publicKey.isSame(b.publicKey))
      assert(!a.privateKey.isSame(b.privateKey))
    } finally {
      Memento(tagA).clear
      Memento(tagB).clear
    }
  }
}
