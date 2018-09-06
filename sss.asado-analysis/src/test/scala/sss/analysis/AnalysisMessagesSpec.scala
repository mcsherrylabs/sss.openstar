package sss.analysis

import org.scalatest.{FlatSpec, Matchers}
import sss.db.Db
import sss.db.datasource.DataSource

/**
  * Created by alan on 11/11/16.
  */
class AnalysisMessagesSpec extends FlatSpec with Matchers {

  implicit val db = Db("analysis.database", DataSource("analysis.database.datasource"))


  "An AnalysisMessages " should " allow write and retrieval" in {
    val sut = new AnalysisMessages(33)

    sut.write("Hellpo world ")
    val ret = sut()

    assert(ret.nonEmpty)
    assert(ret.head.msg == "Hellpo world ")
    assert(ret.head.msgType == AnalysisMessages.error)
  }

  it should " write and retrieve msg type correctly " in {
    val sut = new AnalysisMessages(34)

    sut.write("Hellpo world ", AnalysisMessages.info)
    val ret = sut()
    assert(ret.head.msgType == AnalysisMessages.info)
  }

  it should " retrieve all messages for a block " in {
    val sut = new AnalysisMessages(35)
    (0 to 4) foreach(_ => sut.write("Hellpo world "))
    val ret = sut()
    assert(ret.nonEmpty, "Wrote 5, got none?")
    assert(ret.size == 5, s"Wrote 5 but got back ${ret.size}")
  }

  it should " delete all messages for a block " in {
    val sut = new AnalysisMessages(35)
    sut.delete
    val ret = sut()
    assert(ret.isEmpty, s"Delete 5, but got back ${ret.size}")
  }

  it should " retrieve no messages specific to a block in the presence of other block messages " in {
    val sut = new AnalysisMessages(36)
    val ret = sut()
    assert(ret.isEmpty, s"None written, but got back ${ret.size}")
  }

}
