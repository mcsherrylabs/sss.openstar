package sss.asado.account

import javax.xml.bind.DatatypeConverter

import sss.ancillary.Memento

/**
  * Created by alan on 3/21/16.
  */

private  class PubPrivAccount(val mementoName: String,
        val createIfMissing: Boolean) {

  private val m = Memento(mementoName)

  lazy val account = PrivateKeyAccount(privKey, pubKey)

  private lazy val privKey: Array[Byte] = loadKey._2
  private lazy val pubKey: Array[Byte] = loadKey._1

  private def loadKey: (Array[Byte], Array[Byte]) = {
    m.read match {
      case None => {
        if(createIfMissing) {
          lazy val pkPair = PrivateKeyAccount()
          val privKStr: String = DatatypeConverter.printHexBinary(pkPair.privateKey)
          val pubKStr: String = DatatypeConverter.printHexBinary(pkPair.publicKey)
          m.write(s"$pubKStr:::$privKStr")
          loadKey
        } else throw new Error(s"No key found at $mementoName")
      }
      case Some(str) =>
        val pub_priv = str.split(":::")
        (DatatypeConverter.parseHexBinary(pub_priv(0)), DatatypeConverter.parseHexBinary(pub_priv(1)))

    }
  }

}

object MasterKey {
  private lazy val impl = new PubPrivAccount("masterKey", false)
  lazy val account: PrivateKeyAccount = impl.account
}

object ClientKey {
  private lazy val impl = new PubPrivAccount("clientKey", true)
  lazy val account: PrivateKeyAccount = impl.account
}
