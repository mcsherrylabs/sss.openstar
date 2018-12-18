package sss.openstar.identityledger

import java.util.regex.Pattern

import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by alan on 2/15/16.
  */
class IdentityPatternTest extends FlatSpec with Matchers {

  "Pattern" should "allow _ " in {
    assert(IdentityService.norm("cavan_90") == "cavan_90", "underscore allowed")

  }
}
