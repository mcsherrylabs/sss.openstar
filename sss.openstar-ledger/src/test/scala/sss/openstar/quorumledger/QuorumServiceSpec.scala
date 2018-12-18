package sss.openstar.quorumledger


import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.util.ByteArrayComparisonOps
import sss.db.Db

import scala.reflect.ClassTag
import scala.util.Try

/**
  * Created by alan on 4/22/16.
  */
class QuorumServiceSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  private implicit val db = Db()

  private val ledgerId = 99.toByte


  private val quorumService = Try(QuorumService.create(ledgerId)).toOption.getOrElse(new QuorumService(ledgerId))

  private val id1 = "id1"
  private val id2 = "id2"

  "The quorum service " should " allow a valid add " in {

    assert(quorumService.add("id1") == Set(id1))
    assert(quorumService.candidates() == Set(id1))
  }

  it should " disallow adding the same member twice " in {
    assert(quorumService.add(id1) == Set(id1))
    assert(quorumService.candidates() == Set(id1))
  }

  it should " allow adding another member " in {
    assert(quorumService.add(id2) == Set(id1, id2))
  }

  it should " allow removing another member " in {
    assert(quorumService.remove(id2) == Set(id1))
    assert(quorumService.candidates() == Set(id1))
  }
}
