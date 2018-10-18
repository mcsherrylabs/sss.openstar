package sss.asado.wallet

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.{NodeIdentityManager, PublicKeyAccount}
import sss.asado.balanceledger._
import sss.asado.identityledger.IdentityService
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.wallet.WalletPersistence.Lodgement
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */

class WalletSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  val nm = new NodeIdentityManager(DummySeedBytes)
  object TestBalanceLedgerQuery extends BalanceLedgerQuery {

    var mocks : Map[TxIndex, TxOutput] = Map()

    override def balance: Int = mocks.map(_._2.amount).sum

    override def entry(inIndex: TxIndex): Option[TxOutput] = mocks.get(inIndex)

    override def map[M](f: (TxOutput) => M): Seq[M] = ???

    override def keys: Seq[TxIndex] = ???
  }

  implicit val db = Db()

  val id = UUID.randomUUID().toString.substring(0,8)
  val otherId = UUID.randomUUID().toString.substring(0,8)

  val pKey = PublicKeyAccount(DummySeedBytes(32))
  val otherPKey = PublicKeyAccount(DummySeedBytes(32))
  val wp = new WalletPersistence(id, db)
  val identityService = IdentityService()
  val nId = nm(id, "defaultTag", "phrase12")
  val otherNodeId = nm(otherId, "defaultTag", "phrase12")

  identityService.claim(nId.id, pKey.publicKey, nId.tag)
  identityService.claim(otherNodeId.id, otherPKey.publicKey, otherNodeId.tag)

  val wallet = new Wallet(nId, TestBalanceLedgerQuery, identityService, wp, () => 0, _ => false)

  val txId0 = DummySeedBytes(32)
  val txId1 = DummySeedBytes(32)
  val txId2 = DummySeedBytes(32)

  val txIndex0 = TxIndex(txId0, 0)
  val txIndex1 = TxIndex(txId1, 0)
  val txIndex2 = TxIndex(txId2, 0)

  val funds3000 = Map(txIndex0 -> TxOutput(1000, wallet.encumberToIdentity(someIdentity = nId.id)),
    txIndex1 -> TxOutput(2000, wallet.encumberToIdentity(someIdentity = nId.id)))

  val funds10 = Map(txIndex2 -> TxOutput(10, wallet.encumberToIdentity(someIdentity = nId.id)))

  "A Wallet " should " create a good payment " in {
    assert(wallet.balance(0) == 0)
    TestBalanceLedgerQuery.mocks = funds3000
    wallet.credit(Lodgement(txIndex0, funds3000(txIndex0), 0))
    wallet.credit(Lodgement(txIndex1, funds3000(txIndex1), 0))
    assert(wallet.balance(0) == 3000)
  }

}
