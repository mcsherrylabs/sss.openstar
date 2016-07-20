package messagesender

import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by alan on 7/13/16.
  */
class CircularSeqSpec extends FlatSpec with Matchers {


  "CircularSeq " should " get next in seq " in {
    val cSeq = new CircularSeq(10, 100, 19)
    assert(cSeq.next == 10)
    assert(cSeq.next == 11)
  }

  "A CircularSeq " should " wrap " in {
    val cSeq = new CircularSeq(10, 11, 11)
    assert(cSeq.next == 10)
    assert(cSeq.next == 10)
    assert(cSeq.next == 10)
  }

  "A CircularSeq " should " handle max as skip " in {
    val cSeq = new CircularSeq(9, 11, 11)
    assert(cSeq.next == 9)
    assert(cSeq.next == 10)
    assert(cSeq.next == 9)
  }

  "A CircularSeq " should " handle min as skip " in {
    val cSeq = new CircularSeq(9, 11, 9)
    assert(cSeq.next == 10)
    assert(cSeq.next == 11)
    assert(cSeq.next == 10)
  }

  "A bigger CircularSeq " should " wrap " in {
    val cSeq = new CircularSeq(10, 15, 11)
    assert(cSeq.next == 10)
    assert(cSeq.next == 12)
    assert(cSeq.next == 13)
    assert(cSeq.next == 14)
    assert(cSeq.next == 15)
    assert(cSeq.next == 10)
    assert(cSeq.next == 12)
    assert(cSeq.next == 13)
  }
}
