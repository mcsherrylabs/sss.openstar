package sss.asado.crypto

import java.security.SecureRandom
import java.util.Base64

/**
  * Created by alan on 6/24/16.
  */
object SecureSeedBytes {

  def string(num :Int): String = {
    Base64.getEncoder.encodeToString(apply(num)).substring(0, num)
  }

  def apply(num :Int): Array[Byte] = {
    val bytes = new Array[Byte](num)
    SecureRandom.getInstanceStrong.nextBytes(bytes)
    bytes
  }
}
