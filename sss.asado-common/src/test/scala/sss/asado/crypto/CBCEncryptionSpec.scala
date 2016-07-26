package sss.asado.crypto

import java.nio.charset.StandardCharsets

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
    println(s"CBC STRING IV ${iv}")
    val encrypted = CBCEncryption.encrypt(key, value, iv)
    assert(new String(encrypted, StandardCharsets.UTF_8) != value)
    assert(new String(encrypted, StandardCharsets.UTF_8) != key)

    val decrypted = CBCEncryption.decrypt(key, encrypted, iv)
    assert(new String(decrypted, StandardCharsets.UTF_8) == value)

    intercept[Exception](CBCEncryption.decrypt(key + "!", encrypted, iv))

  }

  it should " encrypt and decrypt a byte array " in {

  val key = "codfsfdIBEanmoreraasdadsads"
  val value = "randonw mesafsd s;klf ;asdasdadlk asdad;lsdkf ;sldkf ;sldkf;aqkq;wkemq"
  val iv = CBCEncryption.newInitVector
  println(s"CBC BYTE IV ${iv}")
  val encrypted = CBCEncryption.encrypt(key, value.getBytes, iv)
  assert(new String(encrypted, StandardCharsets.UTF_8) != value)
  assert(new String(encrypted, StandardCharsets.UTF_8) != key)

  val decrypted = CBCEncryption.decrypt(key, encrypted, CBCEncryption.initVector(iv.asString))
  assert(new String(decrypted, StandardCharsets.UTF_8) == value)

  var decrypted2: Array[Byte] = Array()

  intercept[Exception](decrypted2 = CBCEncryption.decrypt("!" + key, encrypted, iv))
  assert(new String(decrypted2, StandardCharsets.UTF_8) != value)

  }
}


