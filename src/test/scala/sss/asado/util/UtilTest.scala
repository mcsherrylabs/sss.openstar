package sss.asado.util

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.{Account, PrivateKeyAccount}

/**
  * Created by alan on 2/11/16.
  */
class UtilTest extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount(SeedBytes(20))

  "A util " should " generate a new public and private key " in {

    println(s"Address is ${pkPair.address}")
    println(s"Is Valid? ${Account.isValidAddress(pkPair.address)}")

  }


  it should "be able to sign a message and verify the mesage was signed " in {

    val msg = "Holy guacamole, it's a message from Zod"
    val wrongMsg = "Hly guacamole, it's a message from Zod"
    val sig = EllipticCurveCrypto.sign(pkPair.privateKey.array, msg.getBytes)
    assert(EllipticCurveCrypto.verify(sig, msg.getBytes, pkPair.publicKey.array))
    assert(!EllipticCurveCrypto.verify(sig, wrongMsg.getBytes, pkPair.publicKey.array))

  }

  it should "be able to receive a byte stream and verify the tx was signed " in {

  }

}
