package sss.asado.crypto

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.ByteArrayComparisonOps

/**
  * Created by alan on 2/11/16.
  */
class CBCEncryptionSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  "CBC Encryption " should " encrypt and decrypt a string " in {

    val key = "coulsdfsfdIBEanmorera"
    val value = "randonw mesafsd s;klf ;lk ;lsdkf ;sldkf ;sldkf;aqkq;wkemq"
    val iv = CBCEncryption.newInitVector
    val encrypted = CBCEncryption.encrypt(key, value, iv)
    assert(new String(encrypted) != value)
    assert(new String(encrypted) != key)

    val decrypted = CBCEncryption.decrypt(key, encrypted, iv)
    assert(new String(decrypted) == value)

    intercept[Exception](CBCEncryption.decrypt(key + "!", encrypted, iv))

  }

  it should " encrypt and decrypt a byte array " in {

  val key = "coulsdfsfdIBEanmorera"
  val value = "randonw mesafsd s;klf ;lk ;lsdkf ;sldkf ;sldkf;aqkq;wkemq"
  val iv = CBCEncryption.newInitVector
  val encrypted = CBCEncryption.encrypt(key, value.getBytes, iv)
  assert(new String(encrypted) != value)
  assert(new String(encrypted) != key)

  val decrypted = CBCEncryption.decrypt(key, encrypted, CBCEncryption.initVector(iv.asString))
  assert(new String(decrypted) == value)

  intercept[Exception](CBCEncryption.decrypt(key + "!", encrypted, iv))

}
}


