package sss.asado.identityledger

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.PrivateKeyAccount
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

/**
  * Created by alan on 4/22/16.
  */
class IdentityServiceSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  implicit val db = Db()
  val myIdentity = "intothelight_diffferent"
  val myRescuerIdentity = "backupguy_different"

  val key1 = PrivateKeyAccount(DummySeedBytes).publicKey

  "The identity ledger " should " be able to claim an identity to a key" in {

    val acm = IdentityService()
    assert(acm.accountOpt(myIdentity).isEmpty)
    assert(!acm.matches(myIdentity, key1))

    acm.claim(myIdentity, key1)

    assert(acm.accounts(myIdentity).size == 1)
    assert(acm.accountOpt(myIdentity).isDefined)
    assert(acm.account(myIdentity).publicKey.isSame(key1))
    assert(acm.matches(myIdentity, key1))
  }


  it should " prevent adding the same key to an identity twice" in {
    val acm = IdentityService()
    intercept[Exception] (acm.link(myIdentity, key1, "tag2"))
  }

  it should " be able to link a second key to an identity " in {
    val key = PrivateKeyAccount(DummySeedBytes).publicKey
    val acm = IdentityService()
    assert(acm.accountOpt(myIdentity).isDefined)

    acm.link(myIdentity, key, "tag2")
    assert(acm.matches(myIdentity, key))

  }


  it should " be able to find an identity from a key " in {

    val acm = IdentityService()
    assert(acm.identify(key1).isDefined)
    assert(acm.identify(key1).get.identity == myIdentity)

  }

  it should " be able to unlink a key from an identity " in {

    val acm = IdentityService()
    assert(acm.matches(myIdentity, key1))
    acm.unlink(myIdentity, key1)

    assert(!acm.matches(myIdentity, key1))
    assert(!acm.accounts(myIdentity).exists(_.account.publicKey.isSame(key1)))

  }

  it should " prevent linking a non existing rescuer to an identity " in {
    val acm = IdentityService()
    intercept[IllegalArgumentException] (acm.linkRescuer(myIdentity, myRescuerIdentity))
  }

  it should " allow linking an existing rescuer to an identity " in {
    val acm = IdentityService()
    assert(acm.accounts(myRescuerIdentity).isEmpty)
    acm.claim(myRescuerIdentity, key1)
    assert(acm.accounts(myRescuerIdentity).size == 1)
    acm.linkRescuer(myIdentity, myRescuerIdentity)
    val rescuers = acm.rescuers(myIdentity)
    assert(rescuers.size == 1)
    assert(rescuers.contains(myRescuerIdentity))
  }

  it should " allow unlinking an existing rescuer from an identity " in {
    val acm = IdentityService()
    acm.unLinkRescuer(myIdentity, myRescuerIdentity)
    val rescuers = acm.rescuers(myIdentity)
    assert(rescuers.isEmpty)
  }

  it should " prevent released identities from being rescuers " in {
    val acm = IdentityService()
    acm.linkRescuer(myIdentity, myRescuerIdentity)
    val rescuers = acm.rescuers(myIdentity)
    assert(rescuers.contains(myRescuerIdentity))
    assert(acm.accounts(myRescuerIdentity).size == 1)
    acm.unlink(myRescuerIdentity, acm.defaultTag)
    assert(acm.accounts(myRescuerIdentity).isEmpty)
    val emptyRescuers = acm.rescuers(myIdentity)
    assert(emptyRescuers.isEmpty)

  }

}
