package sss.asado.crypto

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.ByteArrayComparisonOps

/**
  * Created by alan on 2/11/16.
  */
class ECBEncryptionSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  val encKey = "coulsdfsfdIBEanmorera2223"
  val encValue = "randonw mesafsd s;klf ;lk ;lsdkf ;sldkf ;sldkf;aqkq;wkemq"

  "ECB Encryption " should " encrypt and decrypt a string " in {


    val encrypted = ECBEncryption.encrypt(encKey, encValue)
    assert(encrypted != encValue)
    assert(encrypted != encKey)

    val decrypted = ECBEncryption.decrypt(encKey, encrypted)
    assert(new String(decrypted) == encValue)

    intercept[Exception](ECBEncryption.decrypt(encKey + "!", encrypted))

  }

  it should "encrypt and decrypt a byte array " in {

    val bytes = SeedBytes(100)
    val encrypted = ECBEncryption.encrypt(encKey, bytes)

    val decrypted = ECBEncryption.decrypt(encKey, encrypted)
    assert(decrypted.sameElements(bytes))

  }
}


