package sss.asado.tools

import sss.asado.account.NodeIdentity

import sss.asado.identityledger.IdentityService.defaultTag
import us.monoid.web.Resty
import sss.asado.util.ByteArrayEncodedStrOps._


/**
  * Created by alan on 6/7/16.
  */
object CreateIdentity {

  def main(args: Array[String]) {

    if(args.length == 2) {
      val ledgerUrl = args(0)
      val identity = args(1)

      require(identity.forall(c => c.isDigit || c.isLower),
        s"Identity ($identity) must be lower case and a simple alpha numeric")


      if (NodeIdentity.keyExists(identity, defaultTag)) {
        println(s"Key exists for identity $identity - unlocking with phrase")
      } else {
        println(s"Creating key for identity $identity - please provide phrase")
        println("(You will need this phrase again to unlock the key)")
      }

      val ni = NodeIdentity.unlockNodeIdentityFromConsole(identity, defaultTag)
      println("...unlocked.")
      val pkey = ni.publicKey.toBase64Str
      val result = new Resty().text(s"$ledgerUrl?claim=$identity&pKey=$pkey")
      println(result)
      if (result.toString.startsWith("ok")) {
        println(s"Identity $identity is now locked to public key $pkey")
        println(s"The public key is identified by tag $defaultTag")
        println(s"The private key corresponding to the public key is unlocked by the password you just typed in.")
      } else {
        println(s"Couldn't register identity $identity - ")
      }
    } else println("Provide the url to claim from and the identity to claim.")
  }
}
