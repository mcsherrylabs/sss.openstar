package sss.asado.account

import javax.xml.bind.DatatypeConverter

import com.typesafe.config.Config
import scorex.crypto.signatures.SigningFunctions.{MessageToSign, PublicKey, Signature}
import sss.ancillary.Memento

/**
  * Created by alan on 3/21/16.
  */
private  class KeyPersister(val mementoName: String,
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

trait NodeIdentity {
  val id: String
  val publicKey: PublicKey
  def sign(msg: MessageToSign): Signature
  def verify(sig: Signature, msg: Array[Byte]): Boolean
}

object NodeIdentity {
  def apply(nodeConfig: Config): NodeIdentity = apply(nodeConfig.getString("bind.nodeId"))
  def apply(nodeId: String): NodeIdentity = {
    val nodeKey = new KeyPersister(nodeId, true).account
    new NodeIdentity {
      override def verify(sig: Signature, msg: Array[Byte]): Boolean = nodeKey.verify(sig, msg)
      override def sign(msg: MessageToSign): Signature = nodeKey.sign(msg)
      override val publicKey: PublicKey = nodeKey.publicKey
      override val id: String = nodeId
    }
  }

}

object ClientKey {
  def apply(tag: String = "clientKey"): PrivateKeyAccount = new KeyPersister(tag, true).account

}
