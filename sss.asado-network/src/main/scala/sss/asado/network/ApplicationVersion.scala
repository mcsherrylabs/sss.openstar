package sss.asado.network

import com.google.common.primitives.Ints

import scala.util.Try

case class ApplicationVersion(firstDigit: Int, secondDigit: Int, thirdDigit: Int) {
  lazy val bytes: Array[Byte] = Ints.toByteArray(firstDigit) ++ Ints.toByteArray(secondDigit) ++ Ints.toByteArray(thirdDigit)
}

object ApplicationVersion {
  val SerializedVersionLength = 4 * 3

  def apply(ver: String): ApplicationVersion = {
    val digits = ver.split("\\.")
    require(digits.length == 3, "The Application Version must be of the form x.x.x")
    ApplicationVersion(digits(0).toInt, digits(1).toInt, digits(2).toInt)
  }

  def parse(bytes: Array[Byte]): Try[ApplicationVersion] = Try {
    require(bytes.length == SerializedVersionLength, "Wrong bytes for application version")
    ApplicationVersion(
      Ints.fromByteArray(bytes.slice(0, 4)),
      Ints.fromByteArray(bytes.slice(4, 8)),
      Ints.fromByteArray(bytes.slice(8, 12))
    )
  }
}
