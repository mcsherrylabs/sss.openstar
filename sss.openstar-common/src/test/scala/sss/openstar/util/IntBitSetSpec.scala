package sss.openstar.util

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.util.Results._

/**
  * Created by alan on 2/11/16.
  */
class IntBitSetSpec extends FlatSpec with Matchers {


  "IntBitSet " should " behave as expected " in {
    assert(!IntBitSet(2).contains(1), "should not overlap ")
    assert(!IntBitSet(4).contains(2), "should not overlap ")
    assert(IntBitSet(7).contains(6), "should overlap ")
    assert(!IntBitSet(6).contains(7), "should overlap ")
    assert(IntBitSet(7).contains(5), "should overlap ")
  }


}
