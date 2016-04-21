package sss.asado.account

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.{EllipticCurveCrypto, SeedBytes}

/**
  * Created by alan on 2/11/16.
  */
class AccountTest extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount(SeedBytes(20))

  "A util " should " generate a new public and private key " in {

    println(s"Address is ${pkPair.address}")
    println(s"Is Valid? ${Account.isValidAddress(pkPair.address)}")

  }


  it should "be able to sign a message and verify the mesage was signed " in {

    val msg = "Holy guacamole, it's a message from Zod"
    val wrongMsg = "Hly guacamole, it's a message from Zod"
    val sig = EllipticCurveCrypto.sign(pkPair.privateKey, msg.getBytes)
    assert(EllipticCurveCrypto.verify(sig, msg.getBytes, pkPair.publicKey))
    assert(!EllipticCurveCrypto.verify(sig, wrongMsg.getBytes, pkPair.publicKey))

  }


}
