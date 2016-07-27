package sss.asado.crypto

import java.nio.charset.StandardCharsets
import java.util

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{FlatSpec, Matchers, PropSpec}
import sss.asado.util.ByteArrayComparisonOps

/**
  * Created by alan on 2/11/16.
  */

class CBCEncryptionSpec extends PropSpec
  with PropertyChecks
  with GeneratorDrivenPropertyChecks
  with Matchers {

  property("decrypted string should match the pre encryption string ") {

    forAll (minSuccessful(50)){ (key: String, value: String) => {
      whenever(key.length > 0 && value.length > 0) {
        val iv = CBCEncryption.newInitVector
        println(s"CBC STRING IV ${iv.asString}")
        val encrypted = CBCEncryption.encrypt(key, value, iv)
        new String(encrypted, StandardCharsets.UTF_8) shouldNot be(value)
        new String(encrypted, StandardCharsets.UTF_8) shouldNot be(key)

        val decrypted = CBCEncryption.decrypt(key, encrypted, iv)
        new String(decrypted, StandardCharsets.UTF_8) should be(value)

        intercept[Exception](CBCEncryption.decrypt(key + "!", encrypted, iv))
      }
    }
    }
  }
  property("decrypted byte array should match the pre encryption array ") {

    forAll (minSuccessful(50)){ (key: String, value: Array[Byte]) => {
      whenever(key.length > 0 && value.length > 0) {
        val iv = CBCEncryption.newInitVector
        println(s"CBC STRING IV ${iv.asString}")
        val encrypted = CBCEncryption.encrypt(key, value, iv)
        encrypted shouldNot be(value)
        new String(encrypted, StandardCharsets.UTF_8) shouldNot be(key)

        val decrypted = CBCEncryption.decrypt(key, encrypted, iv)
        decrypted should be(value)

        intercept[Exception]{
          val badDec = CBCEncryption.decrypt(key + "!", encrypted, iv)
          if(util.Arrays.equals(badDec, value)) {
            println("I've successfully decrypted a value with the wrong key!")
          } else println("At least the decrypted values were different, partial success.")
        }
      }
    }
    }
  }

  property("decrypted string should match the pre encryption string when key is byte array") {

    forAll (minSuccessful(50)){ (key: Array[Byte], value: String) => {
      whenever(key.length > 0 && value.length > 0) {
        val iv = CBCEncryption.newInitVector
        println(s"CBC STRING IV ${iv.asString}")
        val encrypted = CBCEncryption.encrypt(key, value, iv)
        new String(encrypted, StandardCharsets.UTF_8) shouldNot be(value)
        encrypted shouldNot be(key)

        val decrypted = CBCEncryption.decrypt(key, encrypted, iv)
        new String(decrypted, StandardCharsets.UTF_8) should be(value)

        val k: Array[Byte] = key ++ "!".getBytes()
        intercept[Exception](CBCEncryption.decrypt(k, encrypted, iv))
      }
    }
    }
  }

  property("decrypted byte array should match the pre encryption array when key is also byte array") {

    forAll (minSuccessful(50)){ (key: Array[Byte], value: Array[Byte]) => {
      whenever(key.length > 0 && value.length > 0) {
        val iv = CBCEncryption.newInitVector
        println(s"CBC STRING IV ${iv.asString}")
        val encrypted = CBCEncryption.encrypt(key, value, iv)
        encrypted shouldNot be(value)
        encrypted shouldNot be(key)


        val decrypted = CBCEncryption.decrypt(key, encrypted, iv)
        decrypted should be(value)

        val k: Array[Byte] = key ++ "!".getBytes()
        intercept[Exception](CBCEncryption.decrypt(k, encrypted, iv))
      }
    }
    }
  }
}


