package sss.asado.crypto

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.ByteArrayComparisonOps

/**
  * Created by alan on 2/11/16.
  */
class SeedBytesSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  "Seed Bytes " should " return different strings of the right lenght " in {
    val a = SeedBytes.string(25)
    val b = SeedBytes.string(25)
    assert(a.length == 25)
    assert(b.length == 25)
    assert(b != a)
    assert(SeedBytes.string(1).length == 1)
    assert(SeedBytes.string(2000).length == 2000)
    assert(SeedBytes.string(0).length == 0)

  }

}


