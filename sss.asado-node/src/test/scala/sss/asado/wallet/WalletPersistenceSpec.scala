package sss.asado.wallet

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.contract.NullEncumbrance
import sss.asado.crypto.SeedBytes
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.wallet.WalletPersistence.Lodgement
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */

class WalletPersistenceSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  implicit val db = Db()

  val wp = new WalletPersistence(UUID.randomUUID().toString.substring(0,8), db)

  val txId0 = SeedBytes(32)
  val txId1 = SeedBytes(32)

  val txIndex0 = TxIndex(txId0, 0)
  val txOutput0 = TxOutput(50, NullEncumbrance)

  val txIndex1 = TxIndex(txId1, 0)

  "A TxIndex " should " be persistable " in {
    assert(wp.listUnSpent.isEmpty)
    wp.track(Lodgement(txIndex0, txOutput0, 0))
  }


  it should " be retrievable " in {
    assert(wp.listUnSpent.size == 1)
    wp.listUnSpent.foreach { t =>
      assert(t.txIndex == txIndex0)
      assert(t.txOutput == txOutput0)
      assert(t.inBlock == 0)
    }
  }

  it should " handle more than one  index " in {
    wp.track(Lodgement(txIndex1, txOutput0, 0))
    assert(wp.listUnSpent.size == 2)
  }

  it should " allow an index to be marked spent " in {
    wp.markSpent(txIndex0)
    assert(wp.listUnSpent.size == 1)
  }

  it should " ignore remarking an index spent " in {
    wp.markSpent(txIndex0)
    assert(wp.listUnSpent.size == 1)
  }

  it should " allow marking a second index spent" in {
    wp.markSpent(txIndex1)
    wp.markSpent(txIndex0)
    assert(wp.listUnSpent.isEmpty)
  }
}
