package sss.asado.util

/**
  * Created by alan on 2/12/16.
  */
object SeedBytes {

  def apply(num :Int): Array[Byte] = {
    val bytes = new Array[Byte](num)
    scala.util.Random.nextBytes(bytes)
    bytes
  }
}
