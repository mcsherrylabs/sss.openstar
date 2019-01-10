package sss.openstar.common.telemetry.serialize

import org.scalatest.{FlatSpec, Matchers}


import sss.openstar.common.block._
import sss.openstar.common.telemetry._


/**
  * Created by alan on 2/15/16.
  */
class ReportSerializerTest extends FlatSpec with Matchers {


  "Report" should "be correctly serialised and deserialized " in {
    val r = Report("nodeName", 45, Option(BlockId(800, 34)), 99, Seq("1", "2", "3"))
    val asBytes = r.toBytes
    val backAgain = asBytes.toReport
    assert(backAgain === r)
  }


}
