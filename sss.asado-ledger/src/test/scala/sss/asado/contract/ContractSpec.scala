package sss.asado.contract


import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.crypto.SeedBytes
import sss.asado.identityledger.IdentityService
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */
class ContractSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))
  implicit val db = Db()

  import LedgerContext._
  lazy val lc = LedgerContext(Map(blockHeightKey -> 0l, identityServiceKey -> IdentityService()))

  "A single sig " should " unlock a single key contract " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(enc.decumber(Seq(msg.getBytes(), sig), lc, PrivateKeySig))
  }

  it  should " correctly support equality and hashcode " in {

    val otherPkPair = PrivateKeyAccount(SeedBytes(32))
    val enc = SinglePrivateKey(pkPair.publicKey)
    val enc2 = SinglePrivateKey(pkPair.publicKey)
    val enc3 = SinglePrivateKey(otherPkPair.publicKey)
    assert(enc == enc2)
    assert(enc.hashCode() == enc2.hashCode())
    assert(enc != enc3)
    assert(enc2 != enc3)

  }

  it  should " fail if the wrong decumbrance is used " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(!enc.decumber(Seq(msg.getBytes(), sig), lc, NullDecumbrance))
  }

  it  should " fail if the msg is different  " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(!enc.decumber(Seq(msg.getBytes() ++ Array[Byte](0), sig),lc, PrivateKeySig))
  }

  it  should " fail if the sig is different  " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(!enc.decumber(Seq(msg.getBytes(), sig ++ Array[Byte](0)),lc, PrivateKeySig))
  }

  "A null ecumbrance" should " be decumbered by Null decumbrance " in {
    assert(NullEncumbrance.decumber(Seq(), lc, NullDecumbrance))
  }

  it should "  be decumbered by any decumbrance " in {
    assert(NullEncumbrance.decumber(Seq(), lc, PrivateKeySig))
  }

  "An indentity encumbrance " should "be decumbered by any identity key sig " in {

    val myTag = "homepc"
    val myTag2 = "mobile"
    val txId = SeedBytes(32)
    lazy val pkPair = PrivateKeyAccount(SeedBytes(32))
    lazy val pkPair2 = PrivateKeyAccount(SeedBytes(32))
    val myIdentity = "hithereworld"

    val idService: IdentityService  = lc.identityService.get
    idService.claim(myIdentity, pkPair.publicKey, myTag)
    val enc = SingleIdentityEnc(myIdentity, 0)
    val badSig = SingleIdentityDec.createUnlockingSignature(txId, myTag2, pkPair2.sign)
    val goodSig = SingleIdentityDec.createUnlockingSignature(txId, myTag, pkPair.sign)
    assert(enc.decumber(txId +: goodSig, lc, SingleIdentityDec))
    intercept[IllegalArgumentException](enc.decumber(txId +: badSig, lc, SingleIdentityDec))

    idService.link(myIdentity,pkPair2.publicKey, myTag2)
    val goodSigPostLink = badSig
    assert(enc.decumber(txId +: goodSigPostLink , lc, SingleIdentityDec))
  }

}
