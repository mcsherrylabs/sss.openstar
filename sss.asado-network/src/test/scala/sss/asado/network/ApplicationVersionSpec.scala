package sss.asado.network

import org.scalatest.{FlatSpec, Matchers}

class ApplicationVersionSpec extends FlatSpec with Matchers {

  "ApplicationVersion " should " serialise and deserialize to same " in {

    val av = ApplicationVersion(45, 87, Int.MaxValue)
    val av2 = ApplicationVersion.parse(av.bytes)
    assert(av === av2)

  }

  it should "also allow creation through a string " in {
    val f = 354345
    val s = 34335345
    val t = 8888

    val av = ApplicationVersion(s"$f.$s.$t")
    assert(av.firstDigit === f)
    assert(av.secondDigit === s)
    assert(av.thirdDigit === t)
  }

}
