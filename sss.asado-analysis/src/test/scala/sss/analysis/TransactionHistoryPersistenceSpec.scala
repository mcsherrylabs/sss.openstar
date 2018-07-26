package sss.analysis

import org.joda.time.LocalDateTime
import org.scalatest.{FlatSpec, Matchers}
import sss.analysis.TransactionHistory.{ExpandedTx, ExpandedTxElement}
import sss.asado.DummySeedBytes
import sss.asado.ledger.TxId
import sss.db.Db
import sss.db.datasource.DataSource

/**
  * Created by alan on 11/11/16.
  */
class TransactionHistoryPersistenceSpec extends FlatSpec with Matchers {

  implicit val db = Db("analysis.database", DataSource("analysis.database.datasource"))
  val txPeristence = new TransactionHistoryPersistence()

  val txIds: Seq[TxId] = (0 to 2).map(_ => DummySeedBytes(32))
  val whenDate = new LocalDateTime()

  val simpleInOut = ExpandedTx(Seq(ExpandedTxElement(txIds(0), "bob", 34)),
                    Seq(ExpandedTxElement(txIds(0), "alice", 34)), whenDate, 4)

  val multiInOut = ExpandedTx(Seq(ExpandedTxElement(txIds(1), "karl", 34), ExpandedTxElement(txIds(1), "karl", 4)),
    Seq(ExpandedTxElement(txIds(1), "fred", 34)), whenDate, 4)

  val multiInOut2 = ExpandedTx(Seq(ExpandedTxElement(txIds(2), "tayna", 34), ExpandedTxElement(txIds(2), "clare", 34)),
    Seq(ExpandedTxElement(txIds(2), "alice", 34), ExpandedTxElement(txIds(2), "clare", 34)), whenDate, 5)

  def someExpandedTxs = {
    Stream(simpleInOut, multiInOut, multiInOut2)
  }

  "A Tx History Persistence " should " allow write and retrieval" in {

    someExpandedTxs.foreach(txPeristence.write)
    val result = txPeristence.list
    assert(result.size == someExpandedTxs.size)
    someExpandedTxs.foreach { inTx =>
      assert(result.find(_ == inTx).isDefined, s"$inTx not found!")
    }
  }

  it should " dropping all txs " in {
    val result = txPeristence.list
    assert(result.size > 0)
    txPeristence.recreateTable
    assert(txPeristence.list.size == 0)
    someExpandedTxs.foreach(txPeristence.write)
    assert(txPeristence.list.size == someExpandedTxs.size)
  }

  it should "allow retrieval of only bobs transactions" in {
    assert(txPeristence.filter("bob") == Seq(simpleInOut), "Should only get bob's tx")
    println(txPeristence.filter("bob"))
  }

  it should "allow retrieval of only alices transactions" in {
    val alices = txPeristence.filter("alice")
    val target = Seq(simpleInOut, multiInOut2)
    target.foreach { inTx =>
      assert(alices.find(_ == inTx).isDefined, s"$inTx not found!")
    }
    assert(alices.size == target.size)
  }

  it should " fail to retrieve txs that aren't there " in {
    assert(txPeristence.filter("NOONE") == Seq(), "Should get nothing ")
  }

  it should " delete txs related to a given block height " in {
    assert(txPeristence.list.size == 3, "Wrong assumption")
    txPeristence.delete(4)
    assert(txPeristence.list.size == 1, "Not deleted?")
    txPeristence.delete(5)
    assert(txPeristence.list.isEmpty, "Not deleted?")
  }

}
