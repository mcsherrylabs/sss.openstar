package sss.openstar.quorumledger

import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar
import sss.openstar.DummySeedBytes
import sss.openstar.common.block.BlockId
import sss.openstar.eventbus.EventPublish
import sss.openstar.identityledger.TaggedPublicKeyAccount
import sss.openstar.ledger.{LedgerItem, SignedTxEntry}
import sss.openstar.util.ByteArrayComparisonOps
import sss.db.Db
import sss.openstar.account.PrivateKeyAccount

import scala.reflect.ClassTag
import scala.util.Try

/**
  * Created by alan on 4/22/16.
  */
class QuorumLedgerSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  private implicit val db = Db()
  private val node1 = "intothelight"

  private val privateAcc1 = PrivateKeyAccount(DummySeedBytes)
  private val privateAcc2 = PrivateKeyAccount(DummySeedBytes)
  private val privateAcc3 = PrivateKeyAccount(DummySeedBytes)
  private val key1 = privateAcc1.publicKey
  private val key2 = privateAcc2.publicKey

  private val ledgerId = 99.toByte
  private val chainId = 99.toByte

  val evPublish = new EventPublish {
    override def publish[T <: openstar.OpenstarEvent : ClassTag](event: T): Unit = ()
  }


  private val quorumService = Try(
    QuorumService.create(chainId, "id1"))
    .toOption
    .getOrElse(new QuorumService(chainId))


  private def makeSig(msg: Array[Byte], account: PrivateKeyAccount): Array[Byte] = {
    account.sign(msg)
  }

  private def serialize(id: String, tag: String, sig: Array[Byte]) = {
    Seq(
      id.getBytes(StandardCharsets.UTF_8),
      tag.getBytes(StandardCharsets.UTF_8),
      sig
    )
  }

  private def findAccounts(id: String): Seq[TaggedPublicKeyAccount] = {
    id match {
      case "id1" => Seq(TaggedPublicKeyAccount(privateAcc1, "defaultTag"))
      case "id2" => Seq(TaggedPublicKeyAccount(privateAcc2, "defaultTag"))
      case "id3" => Seq(TaggedPublicKeyAccount(privateAcc3, "defaultTag"))
      case _ => Seq.empty
    }
  }


  private val quorumLedger = new QuorumLedger(chainId, ledgerId, quorumService, evPublish, findAccounts)

  private def makeLedgerItem(tx: QuorumLedgerTx, sigs: Seq[Seq[Array[Byte]]] = Seq()): LedgerItem = {
    val ste = SignedTxEntry(tx.toBytes, sigs)
    val le = LedgerItem(ledgerId, tx.txId, ste.toBytes)
    le
  }

  "The quorum ledger " should " have an initial owner" in {

    assert(quorumService.candidates() === Set("id1"))
  }


  it should " disallow adding non existent member " in {
    val item = makeLedgerItem(AddNodeId("nosuchid"))
    intercept[IllegalArgumentException] {
      quorumLedger(item, BlockId(0, 0)).get
    }
    assert(quorumService.candidates() === Set("id1"))
  }

  it should " require a signature when adding a second member" in {

    intercept[IllegalArgumentException] {
      quorumLedger(makeLedgerItem(AddNodeId("id2")), BlockId(0, 0)).get
    }
    //any further adds must be signed by id1
    assert(quorumService.candidates() === Set("id1"))
  }

  it should " reject an incorrect signature when adding a second member" in {

    val tx = AddNodeId("id2")
    val sig = makeSig(tx.txId, privateAcc2) // <-- using id2 (wrong)
    val sigs = Seq(serialize("id1", "defaultTag", sig))
    intercept[IllegalArgumentException] {
      quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0)).get
    }
    assert(quorumService.candidates() === Set("id1"))
  }

  it should " accept an correct signature when adding a second member" in {

    val tx = AddNodeId("id2")
    val sig = makeSig(tx.txId, privateAcc1) // <-- using id1 (correct)
    val sigs = Seq(serialize("id1", "defaultTag", sig))

    quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0))

    //any further adds must be signed by both id1 and id2
    assert(quorumService.candidates() === Set("id1",  "id2"))
  }

  it should " reject an add when not signed by *all* members " in {

    val tx = AddNodeId("id3")
    val sig = makeSig(tx.txId, privateAcc1) // <-- using id1 (correct)
    val sigs = Seq(serialize("id1", "defaultTag", sig))
    intercept[IllegalArgumentException] {
      quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0)).get
    }

    assert(quorumService.candidates() === Set("id1",  "id2"))
  }

  it should " accept an add when signed by *all* members " in {

    val tx = AddNodeId("id3")
    val sig1 = makeSig(tx.txId, privateAcc1) // <-- using id1 (correct)
    val sig2 = makeSig(tx.txId, privateAcc2) // <-- using id1 (correct)
    val sigs = Seq(
      serialize("id1", "defaultTag", sig1),
      serialize("id2", "defaultTag", sig2)
    )

    quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0))
    assert(quorumService.candidates() === Set("id1",  "id2", "id3"))
  }

  it should " reject a remove when not signed by other members " in {

    val tx = RemoveNodeId("id3")
    val sig1 = makeSig(tx.txId, privateAcc1) // <-- using id1 (correct)

    val sigs = Seq(serialize("id1", "defaultTag", sig1))

    intercept[IllegalArgumentException] {
      quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0)).get
    }
    // No change
    assert(quorumService.candidates() === Set("id1",  "id2", "id3"))
  }

  it should " accept a remove when signed by other members " in {

    val tx = RemoveNodeId("id3")
    val sig1 = makeSig(tx.txId, privateAcc1)
    val sig2 = makeSig(tx.txId, privateAcc2)

    val sigs = Seq(
      serialize("id1", "defaultTag", sig1),
      serialize("id2", "defaultTag", sig2)
    )

    quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0))
    assert(quorumService.candidates() === Set("id1",  "id2"))
  }

  /*it should " allow a member to resign " in {

    val tx = RemoveNodeId("id2")
    val sig = makeSig(tx.txId, privateAcc2)
    val sigs = Seq(serialize("id2", "defaultTag", sig))

    quorumLedger(makeLedgerItem(tx, sigs), 0)
    assert(quorumService.members() == Seq("id1"))
  }*/

  it should " reject another remove if no more members remain" in {

    val tx = RemoveNodeId("id2")
    val sig1 = makeSig(tx.txId, privateAcc1)
    val sigs = Seq(
      serialize("id1", "defaultTag", sig1),
    )
    quorumLedger(makeLedgerItem(tx, sigs), BlockId(0, 0))

    val txNoMore = RemoveNodeId("id1")
    val sigNoMore = makeSig(txNoMore.txId, privateAcc1)
    val sigsNoMore = Seq(serialize("id1", "defaultTag", sigNoMore))

    intercept[QuorumLedgerException] {
      quorumLedger(makeLedgerItem(txNoMore, sigsNoMore), BlockId(0, 0)).get
    }
    assert(quorumService.candidates() === Set("id1"))
  }


}
