package sss.asado.quorumledger

import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}
import sss.asado
import sss.asado.DummySeedBytes
import sss.asado.account.PrivateKeyAccount
import sss.asado.eventbus.EventPublish
import sss.asado.identityledger.TaggedPublicKeyAccount
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

import scala.reflect.ClassTag

/**
  * Created by alan on 4/22/16.
  */
class QuorumServiceSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  private implicit val db = Db()

  private val ledgerId = 99.toByte

  private val quorumService = new QuorumService(99.toByte)

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
