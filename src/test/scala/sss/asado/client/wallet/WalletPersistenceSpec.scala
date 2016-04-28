package sss.asado.client.wallet

/**
  * Created by alan on 4/1/16.
  */

import ledger.TxIndex
import sss.asado.util.ByteArrayVarcharOps._
import org.scalatest._
import sss.db.Db


class WalletPersistenceSpec extends MustMatchers
  with WordSpecLike
  {

  implicit val db = Db()
  val wallet = new WalletPersistence(db)

  val fakeTxId = "1234567890"
  val fakeTxId2 = "0987654321"

  "Wallet " must {
    " accept given 'money' " in {

      0 until 10 map { i =>
        wallet.addUnspent(fakeTxId, i, 100)
      }
      val unspent = wallet.findUnSpent
      assert(unspent.size == 10)
      unspent.foreach { u =>
        assert(u.txIndx.txId.toVarChar == fakeTxId)
        assert(u.amount == 100)
        assert(u.isUnspent)
        assert(!u.isKnown)
        assert(!u.isSpent)
      }
    }

    " mark money spent " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 1)
      wallet.markSpent(txIndex)
      assert(wallet.find(txIndex).isDefined)
      assert(wallet.find(txIndex).get.isSpent)
    }

    " money marked spent cannot be found unspent " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 1)
      val found = wallet.findUnSpent.filter(_.txIndx == txIndex)
      assert(found.size == 0)
    }

    " but can be found " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 1)
      val found = wallet.find(txIndex)
      assert(found.isDefined)
      assert(found.get.isSpent)
    }

    " and can be filtered " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 1)
      val found = wallet.filter(txIndex.txId)
      assert(found.size == 10)
    }

    " and can mark tx confirmed " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 2)
      val found = wallet.confirm(txIndex.txId, 1)
      assert(found == 1)
    }

    " and returns the correct num confirms " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 2)
      val found = wallet.confirm(txIndex.txId, 11)
      assert(found == 2)
    }

    " and marks spending tx spent " in {
      val txIndex = TxIndex(fakeTxId.toByteArray, 5)
      val entry = wallet.find(txIndex).get
      val entry2 = wallet.addUnspent(fakeTxId2, 0, 100)

      wallet.spendingTx(entry2, txIndex.txId)
      var confirms = wallet.confirm(txIndex.txId, 999)
      assert(confirms == 3)
      assert(wallet.find(TxIndex(fakeTxId2.toByteArray, 0)).get.isUnspent)
      confirms = wallet.confirm(txIndex.txId, 4)
      assert(confirms == 4)
      assert(wallet.find(TxIndex(fakeTxId2.toByteArray, 0)).get.isSpent)
    }
  }






}

