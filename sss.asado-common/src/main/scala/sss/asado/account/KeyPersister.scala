package sss.asado.account


import sss.ancillary.{Logging, Memento}
import sss.asado.crypto.ECBEncryption._
import sss.asado.util.ByteArrayEncodedStrOps._


import scala.io.StdIn

/**
  * Persists the key pair, tag and identity...
  *
  * @param mementoName
  * @param createIfMissing
  * @param phrase
  * @param tag
  */
private class KeyPersister(val mementoName: String,
                            val createIfMissing: Boolean,
                           phrase: String, tag: String) extends Logging {

  require(phrase.length > 7, "Password must be 8 characters or more." )
  require(tag.length > 0, "Tag cannot be an empty string" )

  private val m = KeyPersister.memento(mementoName, tag)

  lazy val account = PrivateKeyAccount(privKey, pubKey)

  private lazy val privKey: Array[Byte] = loadKey._2
  private lazy val pubKey: Array[Byte] = loadKey._1


  private def loadKey: (Array[Byte], Array[Byte]) = {
    m.read match {
      case None => {
        if(createIfMissing) {
          lazy val pkPair = PrivateKeyAccount()
          val privKStr: String = pkPair.privateKey.toBase64Str
          val pubKStr: String = pkPair.publicKey.toBase64Str
          val encrypted = encrypt(phrase, privKStr)
          val hashedPhrase = PasswordStorage.createHash(phrase)
          val created = s"$pubKStr:::$hashedPhrase:::$encrypted"
          log.debug(s"CREATED - ${created}")
          m.write(created)
          loadKey
        } else throw new Error(s"No key found at $mementoName")
      }
      case Some(str) =>
        val aryOfSecuredKeys = str.split(":::")
        require(aryOfSecuredKeys.length == 3,
          s"File $mementoName is corrupt. Restore from backup or set up a new key.")
        val pubKStr = aryOfSecuredKeys(0)
        val hashedPhrase = aryOfSecuredKeys(1)
        val encryptedPrivateKey = aryOfSecuredKeys(2)
        log.debug(s"""OUT -  ${aryOfSecuredKeys.mkString(":::")}""")
        require(PasswordStorage.verifyPassword(phrase, hashedPhrase), "Incorrect password")

        val decryptedKey = decrypt(phrase, encryptedPrivateKey )
        (pubKStr.toByteArray, decryptedKey.toByteArray)

    }
  }

}

private object KeyPersister {
  def deleteKey(identity: String, tag: String) = memento(identity, tag).clear
  def keyExists(identity: String, tag: String): Boolean = memento(identity, tag).read.isDefined
  def memento(identity: String, tag: String): Memento = Memento(s"$identity.$tag")

}

