package sss.asado.util

import javax.xml.bind.DatatypeConverter

import sss.ancillary.Memento
import sss.asado.account.PrivateKeyAccount

/**
  * Created by alan on 3/21/16.
  */
object RootKey {

  private val m = Memento("rootKey")

  lazy val account = PrivateKeyAccount(privKey, pubKey)

  lazy val privKey: Array[Byte] = loadKey._2
  lazy val pubKey: Array[Byte] = loadKey._1

  private def loadKey: (Array[Byte], Array[Byte]) = {
    m.read match {
      case None => {
        lazy val pkPair = PrivateKeyAccount(SeedBytes(20))
        val privKStr: String = DatatypeConverter.printHexBinary(pkPair.privateKey)
        val pubKStr: String = DatatypeConverter.printHexBinary(pkPair.publicKey)
        m.write(s"$pubKStr:::$privKStr")
        loadKey
      }
      case Some(str) =>
        val pub_priv = str.split(":::")
        (DatatypeConverter.parseHexBinary(pub_priv(0)), DatatypeConverter.parseHexBinary(pub_priv(1)))

    }
  }
}
