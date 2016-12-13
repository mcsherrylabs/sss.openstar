package sss.asado.crypto

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}


/**
  * Created by alan on 6/3/16.
  */
object CBCEncryption {

  trait InitVector {
    val bytes: Array[Byte]
    val asString: String
  }

  def initVector(ivAsString: String): InitVector = new InitVector {
    override lazy val bytes = Base64.getDecoder.decode(ivAsString)
    override lazy val asString: String = ivAsString
  }

  def initVector(initVectorBytes: Array[Byte]) = new InitVector {
    override lazy val bytes = initVectorBytes
    override lazy val asString: String = Base64.getEncoder.encodeToString(bytes)
  }

  def newInitVector = initVector( SecureSeedBytes(16))

  def encrypt(key: String, value: String, iv: InitVector): Array[Byte] = {
    encrypt(key, value.getBytes(UTF_8), iv)
  }

  def encrypt(key: String, value: Array[Byte], iv: InitVector): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.ENCRYPT_MODE, keyToSpec(key), new IvParameterSpec(iv.bytes))
    Base64.getEncoder.encode(cipher.doFinal(value))
  }

  def encrypt(key: Array[Byte], value: String, iv: InitVector): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.ENCRYPT_MODE, keyToSpec(key), new IvParameterSpec(iv.bytes))
    Base64.getEncoder.encode(cipher.doFinal(value.getBytes(UTF_8)))
  }

  def encrypt(key: Array[Byte], value: Array[Byte], iv: InitVector): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.ENCRYPT_MODE, keyToSpec(key), new IvParameterSpec(iv.bytes))
    Base64.getEncoder.encode(cipher.doFinal(value))
  }

  def decrypt(key: Array[Byte], encryptedValue: Array[Byte], iv: InitVector): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.DECRYPT_MODE, keyToSpec(key), new IvParameterSpec(iv.bytes))
    cipher.doFinal(Base64.getDecoder.decode(encryptedValue))
  }

  def decrypt(key: String, encryptedValue: Array[Byte], iv: InitVector): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.DECRYPT_MODE, keyToSpec(key), new IvParameterSpec(iv.bytes))
    cipher.doFinal(Base64.getDecoder.decode(encryptedValue))
  }

  def keyToSpec(keyBytes: Array[Byte]): SecretKeySpec = {
    val sha: MessageDigest = MessageDigest.getInstance("SHA-512")
    val keyBytesDigested = sha.digest(keyBytes)
    val keyBytesFirst16 = java.util.Arrays.copyOf(keyBytesDigested, 16)
    new SecretKeySpec(keyBytesFirst16, "AES")
  }

  def keyToSpec(key: String): SecretKeySpec = {
    var keyBytes: Array[Byte] = key.getBytes(UTF_8)
    keyToSpec(keyBytes)
  }

  private def makeCipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

}
