package sss.asado.account

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.crypto.SeedBytes
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

/**
  * Created by alan on 4/22/16.
  */
class AccountManagerSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  implicit val db = Db()
  val myNode= "myNode"
  val key1 = PrivateKeyAccount(SeedBytes(32)).publicKey

  "An account Manager " should " be able to link a node id to a key" in {

    val acm = AccountManager()
    assert(acm.accountOpt(myNode).isEmpty)
    assert(!acm.matches(myNode, key1))

    acm.link(myNode, key1)

    assert(acm.accountOpt(myNode).isDefined)
    assert(acm.account(myNode).publicKey.isSame(key1))
    assert(acm.matches(myNode, key1))
  }

  it should " be able to link a second key to a node id" in {
    val key = PrivateKeyAccount().publicKey
    val acm = AccountManager()
    assert(acm.accountOpt(myNode).isDefined)

    acm.link(myNode, key)
    assert(acm.matches(myNode, key))

  }

  it should " be able to find a node id from a key " in {

    val acm = AccountManager()
    assert(acm.nodeId(key1).isDefined)
    assert(acm.nodeId(key1).get == myNode)

  }

  it should " be able to unlink a key from a node id" in {

    val acm = AccountManager()
    assert(acm.matches(myNode, key1))
    acm.unlink(myNode, key1)

    assert(!acm.matches(myNode, key1))
    assert(acm.accounts(myNode).find(_.publicKey.isSame(key1)).isEmpty)

  }
}
