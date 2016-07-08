package sss.asado.crypto

import java.security.SecureRandom
import java.util.Base64

/**
  * Created by alan on 2/12/16.
  */
object SeedBytes {

  def string(num :Int): String = {
    Base64.getEncoder.encodeToString(apply(num)).substring(0, num)
  }

  def apply(num :Int): Array[Byte] = {
    require(num >= 0, "Can't make array of bytes with negative length.")
    val bytes = new Array[Byte](num)
    new SecureRandom().nextBytes(bytes)
    bytes
  }
}

