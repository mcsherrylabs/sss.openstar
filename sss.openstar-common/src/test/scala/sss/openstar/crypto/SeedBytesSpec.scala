package sss.openstar.crypto

import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import sss.openstar.util.ByteArrayComparisonOps

/**
  * Strong randomness is just too slow.
  */
@DoNotDiscover
class SeedBytesSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  // Strong randomness is just too slow.
  lazy val seedBytes = new SeedBytes {}

  "Seed Bytes " should " return bytes arrays " in {
    val a = seedBytes.randomSeed(3)
    val b = seedBytes.secureSeed(3)
    assert(a.length == 3)
    assert(b.length == 3)
    assert(b !== a)
  }

  it should " call strongBytes without error " in {
    // use 1 here as the strong can block.
    val a = seedBytes.strongSeed(1)
    assert(a.length == 1)

  }

}


