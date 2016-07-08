package sss.asado.tools

import sss.asado.account.NodeIdentity

import sss.asado.identityledger.IdentityService.defaultTag
import us.monoid.web.Resty
import sss.asado.util.ByteArrayVarcharOps._


/**
  * Created by alan on 6/7/16.
  */
object CreateIdentity {

  def main(args: Array[String]) {
    val ledgerUrl = args(0)
    val identity = args(1)

    require(identity.forall(c => c.isDigit || c.isLower),
      s"Identity ($identity) must be lower case and a simple alpha numeric")

    println("You will need this password again to unlock the key.")
    val ni = NodeIdentity.unlockNodeIdentityFromConsole(identity, defaultTag)
    val pkey = ni.publicKey.toVarChar
    val result = new Resty().text(s"$ledgerUrl?claim=$identity&pKey=$pkey")
    if(result.toString == "Ok") {
      println(s"Identity $identity is now locked to public key $pkey")
      println(s"The public key is identified by tag $defaultTag")
      println(s"The private key corresponding to the public key is unlocked by the password you just typed in.")
    } else {
      println(s"Couldn't register identity $identity - ")
      println(result)
    }


  }
}
