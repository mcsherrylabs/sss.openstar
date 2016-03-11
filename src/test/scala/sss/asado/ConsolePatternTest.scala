package sss.asado

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.console.ConsolePattern

/**
  * Created by alan on 2/11/16.
  */
class ConsolePatternTest extends FlatSpec with Matchers {


  val patterns = new ConsolePattern {}

  "A connect pattern " should " match " in {

    "connect 127.0.0.1:8076" match {
      case patterns.connectPeerPattern(ip, port) => {
        assert(ip === "127.0.0.1")
        assert(port === "8076")
      }
    }


  }




}
