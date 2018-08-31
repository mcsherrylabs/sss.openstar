package sss.asado.util

import org.scalatest.{FlatSpec, Matchers}
import Results._

/**
  * Created by alan on 2/11/16.
  */
class ResultsSpec extends FlatSpec with Matchers {

  val notTrue = "not true"
  val notFound1 = "not found1"
  val notFound2 = "not found2"

  def isSomething(b: Boolean): Boolean = b
  def optSomething[S](o: Option[S]): Option[S] = o

  "Results " should " return result" in {

    val r = isSomething(true).orErrMsg(notTrue) ifOk { b =>
      optSomething(None).orErrMsg(notFound1)
    } ifNotOk {
      optSomething(Some("Got it")).orErrMsg(notFound2)
    }

    assert(r.isOk)
    assertThrows[Exception](r.errors)
    assert(r.result === "Got it")
  }

  "Results " should " accumulate correctly " in {

    val r = isSomething(false).orErrMsg(notTrue) andThen {
      optSomething(None).orErrMsg(notFound1)
    } ifNotOk {
      optSomething(None).orErrMsg(notFound2)
    }

    assert(!r.isOk)
    assertThrows[Exception](r.result)
    assert(r.errors === List(notTrue, notFound1, notFound2))
  }

  "Results " should " type pass through params ok " in {

    optSomething(Some(Long.MaxValue)).orErrMsg(notTrue) ifOk { l =>
      assert(l === Long.MaxValue)
      ok()
    }
  }

  "Results " should " return correct error messages " in {

    val r = isSomething(true).orErrMsg(notTrue) ifOk { b =>
      assert(b === true)
      optSomething(None).orErrMsg(notFound1)
    } ifNotOk {
      optSomething(None).orErrMsg[String](notFound2)
    }

    assert(!r.isOk)
    assertThrows[Exception](r.result)
    assert(r.errors === List(notFound1, notFound2))
  }

  "Results " should " not call code unnecessarily " in {

    val r = isSomething(true).orErrMsg(notTrue) ifNotOk {
      fail("Shouldn't get here")
    }
  }

  "Results " should "pattern match nicely " in {
    isSomething(true).orErrMsg(notTrue) match {
      case Left(b)         =>
      case Right(List("")) =>
    }
  }

}
