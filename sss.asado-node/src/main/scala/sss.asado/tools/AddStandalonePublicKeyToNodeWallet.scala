package sss.asado.tools

import sss.asado.account.PublicKeyAccount
import sss.asado.nodebuilder._
import sss.asado.wallet.{PublicKeyTracker}
import sss.asado.util.ByteArrayEncodedStrOps.Base64StrToByteArray


/**
  * Created by alan on 6/7/16.
  */
object AddStandalonePublicKeyToNodeWallet {

  class LoadDb(val configName: String) extends
    DbBuilder with
    NodeConfigBuilder with
    ConfigBuilder

  def main(args: Array[String]) {

    if(args.length == 3) {

      val dbLoader = new LoadDb(args(0))
      import dbLoader.db

      val identity = args(1)
      val tracker = new PublicKeyTracker(identity)
      val pKey64Str = args(2)
      val pKey = pKey64Str.toByteArray
      PublicKeyAccount(pKey)

      if(tracker.isTracked(pKey)) {
        println("Key already in nodes list.")
      } else {
        tracker.trackBase64(pKey64Str)
      }
      println(s"Keys for $identity now ")
      tracker.keys foreach (println(_))

    } else println("Provide the node config string, the " +
      "identity who's wallet the key should appear in, and " +
      "the base 64 Hex representation of the public key")
  }
}
