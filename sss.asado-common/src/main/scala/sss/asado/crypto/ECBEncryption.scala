package sss.asado.crypto

import java.nio.charset.StandardCharsets.UTF_8
import java.security.{KeyFactory, MessageDigest, PrivateKey}
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
/**
  * Created by alan on 6/24/16.
  */
object ECBEncryption {


  def encrypt(key: String, value: String): String = {
    val encoded = encrypt(key, value.getBytes(UTF_8))
    new String(encoded, UTF_8)
  }

  def encrypt(key: String, value: Array[Byte]): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.ENCRYPT_MODE, keyToSpec(key))
    Base64.getEncoder.encode(cipher.doFinal(value))
  }

  def decrypt(key: String, encryptedValue: String): String = {
    val cipher = makeCipher
    cipher.init(Cipher.DECRYPT_MODE, keyToSpec(key))
    new String(cipher.doFinal(Base64.getDecoder.decode(encryptedValue)),UTF_8)
  }

  def decrypt(key: String, encryptedValue: Array[Byte]): Array[Byte] = {
    val cipher = makeCipher
    cipher.init(Cipher.DECRYPT_MODE, keyToSpec(key))
    cipher.doFinal(Base64.getDecoder.decode(encryptedValue))
  }

  def keyToSpec(key: String): SecretKeySpec = {
    var keyBytes: Array[Byte] = (SALT + key).getBytes(UTF_8)
    val sha: MessageDigest = MessageDigest.getInstance("SHA-512")
    keyBytes = sha.digest(keyBytes)
    keyBytes = java.util.Arrays.copyOf(keyBytes, 16)
    new SecretKeySpec(keyBytes, "AES")
    //val kf = KeyFactory.getInstance("RSA");
    //val pk : PrivateKey= kf.generatePrivate(privateKeySpec)
  }


  private def makeCipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")

  private val SALT: String =
    "afd69ec6d5e94d779cddAfb3b98cccd3F0BePojHgh1HgNK0GhL"
}