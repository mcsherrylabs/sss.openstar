package sss.asado.account

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.EllipticCurveCrypto

/**
  * Created by alan on 2/11/16.
  */
class AccountSpec extends FlatSpec with Matchers {

  lazy val pkPair = PrivateKeyAccount()

  "A util " should " generate a new public and private key " in {

    assert(Account.isValidAddress(pkPair.address))

  }


  it should "be able to sign a message and verify the mesage was signed " in {

    val msg = "Holy guacamole, it's a message from Zod"
    val wrongMsg = "Hly guacamole, it's a message from Zod"
    val sig = EllipticCurveCrypto.sign(pkPair.privateKey, msg.getBytes)
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

}
