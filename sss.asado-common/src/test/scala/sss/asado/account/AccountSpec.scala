package sss.asado.account



import javax.xml.bind.DatatypeConverter

import org.scalatest.{FlatSpec, Matchers}
import scorex.crypto.signatures.Curve25519
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.ByteArrayEncodedStrOps._

/**
  * Created by alan on 2/11/16.
  */
class AccountSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  lazy val pkPair = PrivateKeyAccount()

  "An account " should " generate a new public and private key " in {

    assert(Account.isValidAddress(pkPair.address))

  }

  it should " transform keys to string and back again  " in {
    //3D3FB6F8C86AD2D6A54902DC5F642D0AC2AD4F21FAEE15B940C5260D5B05EC30
    val pkStr = pkPair.publicKey.toBase64Str
    val backAgain = pkStr.toByteArray
    assert(backAgain.length === 32)
    //assert(pkPair.publicKey sameElements(backAgain))
    //assert(pkPair.publicKey.length === 32)
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
    val sig = pkPair.sign(msg.getBytes)
    assert(Curve25519.verify(sig, msg.getBytes, pkPair.publicKey))
    assert(!Curve25519.verify(sig, wrongMsg.getBytes, pkPair.publicKey))

  }


  it should " consistently generate addresses from public keys (even if addresses are never used)" in {

    val addr = pkPair.address
    assert(Account.isValidAddress(addr))

    val address = Account.fromPubkey(pkPair.publicKey)
    assert(Account.isValidAddress(address))

    val pkAgain = new PublicKeyAccount(pkPair.publicKey)
    assert(address === Account.fromPubkey(pkAgain.publicKey))
  }

  /*"A client key " should " provide a unique new pkPair " in {

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
  }*/
}
