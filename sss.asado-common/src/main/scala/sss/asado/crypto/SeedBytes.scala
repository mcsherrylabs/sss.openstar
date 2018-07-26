package sss.asado.crypto

import java.security.SecureRandom

import util.Random

/**
  Make this the indirection to getting 'random' bytes
  */
trait SeedBytes {

  /*
  https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/
   */
  private lazy val secureRandom = new SecureRandom()
  private lazy val strongSecureRandom = SecureRandom.getInstanceStrong
  private lazy val random = new Random()

  private def getBytes(num: Int,r :Random) = {
    val bytes = new Array[Byte](num)
    r.nextBytes(bytes)
    bytes
  }

  /**
    * Uses SecureRandom.getInstanceStrong in production
    * @param num
    * @return
    */
  def strongSeed(num: Int) = getBytes(num, strongSecureRandom)

  /**
    * uses a SecureRandom instance
    * @param num
    * @return
    */
  def secureSeed(num: Int) = getBytes(num, secureRandom)

  /**
    * Uses Random() to generate
    *
    * @param num
    * @return
    */
  def randomSeed(num: Int) = getBytes(num, random)
}

object SeedBytes extends SeedBytes

