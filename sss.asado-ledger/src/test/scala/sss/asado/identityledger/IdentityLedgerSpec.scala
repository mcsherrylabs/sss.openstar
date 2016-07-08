package sss.asado.identityledger

import org.scalatest.{FlatSpec, Matchers}
import scorex.crypto.signatures.SigningFunctions.PublicKey
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.account.PrivateKeyAccount
import sss.db.Db

/**
  * Created by alan on 4/22/16.
  */
class IdentityLedgerSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  implicit val db = Db()
  val myIdentity= "intothelight"
  val rescuerIdentity = "someguy"
  val privateAcc1 = PrivateKeyAccount()
  val privateAcc2 = PrivateKeyAccount()
  val key1 = privateAcc1.publicKey
  val key2 = privateAcc2.publicKey
  val ledgerId = 99.toByte
  val idService = IdentityService()
  val identityLedger = new IdentityLedger(ledgerId, idService)
  val tagForKey2 = "sometag"

  "The identity ledger " should " be able to claim an identity to a key" in {

    val le = makeClaim()
    identityLedger(le, 0)

    assert(idService.account(myIdentity).publicKey isSame key1)
  }

  it should " prevent a second claim to the same identity " in {

    val le = makeClaim()
    intercept[IllegalArgumentException](identityLedger(le, 0))

  }

  private def makeClaim(identity: String = myIdentity, key: PublicKey = key1): LedgerItem = {
    val claim = Claim(identity, key)
    val ste = SignedTxEntry(claim.toBytes)
    val le = LedgerItem(ledgerId, claim.txId, ste.toBytes)
    le
  }

  it should " be able to link a second key to an identity " in {

    val link = Link(myIdentity, key2, tagForKey2)
    val sig = privateAcc1.sign(link.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(link.toBytes, sigs)
    val le = LedgerItem(ledgerId, link.txId, ste.toBytes)
    identityLedger(le, 0)

    val pKey = idService.account(myIdentity, tagForKey2)
    assert(pKey.publicKey isSame key2)

  }

  it should " be able to unlink a key by key from an identity " in {

    val unlink = UnLinkByKey(myIdentity, key1)
    val sig = privateAcc1.sign(unlink.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(unlink.toBytes, sigs)
    val le = LedgerItem(ledgerId, unlink.txId, ste.toBytes)
    identityLedger(le, 0)

    assert(idService.accountOpt(myIdentity).isEmpty)
    assert(idService.accountOpt(myIdentity, tagForKey2).isDefined)

  }


  it should " prevent unlink from a previously unlinked key! " in {

    val unlink = UnLink(myIdentity, tagForKey2)
    val sig = privateAcc1.sign(unlink.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(unlink.toBytes, sigs)
    val le = LedgerItem(ledgerId, unlink.txId, ste.toBytes)
    intercept[IllegalArgumentException] {identityLedger(le, 0)}
  }

  it should " allow unlink from a valid key " in {

    val unlink = UnLink(myIdentity, tagForKey2)
    val sig = privateAcc2.sign(unlink.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(tagForKey2.getBytes, sig))
    val ste = SignedTxEntry(unlink.toBytes, sigs)
    val le = LedgerItem(ledgerId, unlink.txId, ste.toBytes)
    identityLedger(le, 0)
    assert(idService.accountOpt(myIdentity, tagForKey2).isEmpty)
  }

  it should " allow reclaiming an identity with no linked keys " in {

    val le  = makeClaim()
    identityLedger(le, 0)
    assert(idService.account(myIdentity).publicKey isSame key1)
  }

  it should " allow adding a rescuer " in {

    identityLedger(makeClaim(rescuerIdentity, key2), 0)
    assert(idService.accountOpt(rescuerIdentity).isDefined)
    val link = LinkRescuer(rescuerIdentity, myIdentity)
    val sig = privateAcc1.sign(link.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(link.toBytes, sigs)
    val le = LedgerItem(ledgerId, link.txId, ste.toBytes)
    identityLedger(le, 0)

    assert(idService.rescuers(myIdentity).contains(rescuerIdentity))
  }

  it should " prevent adding a self signed rescuer " in {

    val link = LinkRescuer(rescuerIdentity, myIdentity)
    val sig = privateAcc2.sign(link.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(link.toBytes, sigs)
    val le = LedgerItem(ledgerId, link.txId, ste.toBytes)
    intercept[IllegalArgumentException](identityLedger(le, 0))
  }


  it should " allow rescue from an approved rescuer (followed by removal of rescuer) " in {
    val newKeyAcc = PrivateKeyAccount()
    val rescue = Rescue(rescuerIdentity, myIdentity, newKeyAcc.publicKey, "rescued")
    val sig = privateAcc2.sign(rescue.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(rescue.toBytes, sigs)
    val le = LedgerItem(ledgerId, rescue.txId, ste.toBytes)
    identityLedger(le, 0)

    {
      // Now check it by removing the rescuer with the new rescue key :D

      val unLink = UnLinkRescuer(rescuerIdentity, myIdentity)
      val sig = newKeyAcc.sign(unLink.txId)
      val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq("rescued".getBytes, sig))
      val ste = SignedTxEntry(unLink.toBytes, sigs)
      val le = LedgerItem(ledgerId, unLink.txId, ste.toBytes)
      identityLedger(le, 0)
    }
    assert(!idService.rescuers(myIdentity).contains(rescuerIdentity))
  }



  it should " revent rescue from an unapproved rescuer " in {

    val randomerIdentity = "randomer"
    val newKeyAcc = PrivateKeyAccount()
    identityLedger(makeClaim(randomerIdentity, key2), 0)
    val rescue = Rescue(randomerIdentity, myIdentity, newKeyAcc.publicKey, "rescued")
    val sig = privateAcc2.sign(rescue.txId)
    val sigs: Seq[Seq[Array[Byte]]] = Seq(Seq(idService.defaultTag.getBytes, sig))
    val ste = SignedTxEntry(rescue.toBytes, sigs)
    val le = LedgerItem(ledgerId, rescue.txId, ste.toBytes)
    intercept[IllegalArgumentException](identityLedger(le, 0))
  }

}
