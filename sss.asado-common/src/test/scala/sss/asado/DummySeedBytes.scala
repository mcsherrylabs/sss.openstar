package sss.asado

import sss.asado.crypto.SeedBytes

object DummySeedBytes extends SeedBytes {

  lazy val cachedBytes : Array[Byte] = super.randomSeed(500)

  def apply(num :Int) = randomSeed(num)

  override def randomSeed(num: Int) = super.randomSeed(5 ) ++ cachedBytes.take(num - 5)
  override def secureSeed(num: Int) = super.randomSeed(5 ) ++ cachedBytes.take(num - 5)
  override def strongSeed(num: Int) = super.randomSeed(5 ) ++ cachedBytes.take(num - 5)

}
