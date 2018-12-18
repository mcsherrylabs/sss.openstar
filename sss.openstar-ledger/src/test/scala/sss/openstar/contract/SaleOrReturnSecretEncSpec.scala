package sss.openstar.contract

import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.identityledger.IdentityService
import sss.openstar.util.ByteArrayComparisonOps
import sss.db.Db
import sss.openstar.account.PrivateKeyAccount

/**
  * Created by alan on 2/15/16.
  */
class SaleOrReturnSecretEncSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  implicit val db = Db()

  import LedgerContext._
  lazy val lc = LedgerContext(Map(blockHeightKey -> 0l, identityServiceKey -> IdentityService()))

  val myTag = "homepc"
  val myTag2 = "mobile"
  val txId = DummySeedBytes.randomSeed(32)
  lazy val pkPair = PrivateKeyAccount(DummySeedBytes.randomSeed(32))
  lazy val pkPair2 = PrivateKeyAccount(DummySeedBytes.randomSeed(32))


  val idService: IdentityService  = lc.identityService.get

  val sellerIdentity = "sellerIdentity"
  val buyerId = "buyerId"
  val secret = "ohnowhathavetheydone"
  val badSecret = s"$secret!"
  
  val returnBlockHeight = 10

  idService.claim(sellerIdentity, pkPair.publicKey, myTag)
  idService.claim(buyerId, pkPair2.publicKey, myTag2)


  "A sale or encumbrance " should "be decumbered by identity key sig with the correct secret " in {

    val enc = SaleOrReturnSecretEnc(sellerIdentity, buyerId, secret, returnBlockHeight )

    val sig = SaleSecretDec.createUnlockingSignature(
      txId, myTag2, pkPair2.sign, secret.getBytes(StandardCharsets.UTF_8))
    assert(enc.decumber(txId +: sig, lc, SaleSecretDec))

  }

  it should "fail if the secret is bad" in {
    val enc = SaleOrReturnSecretEnc(sellerIdentity, buyerId, secret, returnBlockHeight )

    val sig = SaleSecretDec.createUnlockingSignature(
      txId, myTag2, pkPair2.sign, badSecret.getBytes(StandardCharsets.UTF_8))
    assert(!(enc.decumber(txId +:sig, lc, SaleSecretDec)), "Should not decumber.... ")

  }

  it should " fail if the identity is bad  " in {

    val enc = SaleOrReturnSecretEnc(sellerIdentity, buyerId + "!", secret, returnBlockHeight )

    val sig = SaleSecretDec.createUnlockingSignature(
      txId, myTag2, pkPair2.sign, secret.getBytes(StandardCharsets.UTF_8))
    intercept[Exception](!enc.decumber(txId +:sig, lc, SaleSecretDec))

  }

  it should " succeed even if it now returnable " in {

    val enc = SaleOrReturnSecretEnc(sellerIdentity, buyerId, secret, returnBlockHeight )
    val lc = LedgerContext(Map(blockHeightKey -> 11l, identityServiceKey -> IdentityService()))
    val sig = SaleSecretDec.createUnlockingSignature(
      txId, myTag2, pkPair2.sign, secret.getBytes(StandardCharsets.UTF_8))
    assert(enc.decumber(txId +:sig, lc, SaleSecretDec))

  }

  it should " be impossible to decumber by seller before block height " in {
    val enc = SaleOrReturnSecretEnc(sellerIdentity, buyerId, secret, returnBlockHeight )
    val lc = LedgerContext(Map(blockHeightKey -> 9l, identityServiceKey -> IdentityService()))
    val sig = ReturnSecretDec.createUnlockingSignature(
      txId, myTag2, pkPair2.sign)
    intercept[Exception](enc.decumber(txId +:sig, lc, ReturnSecretDec))
  }

  it should " be possible to decumber by seller after block height " in {
    val enc = SaleOrReturnSecretEnc(sellerIdentity, buyerId, secret, returnBlockHeight )
    val lc2 = LedgerContext(Map(blockHeightKey -> 10l, identityServiceKey -> IdentityService()))

    val sig = ReturnSecretDec.createUnlockingSignature(
      txId, myTag, pkPair.sign)
    assert(enc.decumber(txId +:sig, lc2, ReturnSecretDec))
  }

  it should " recognise the claimant and seller are reversed" in {
    val enc = SaleOrReturnSecretEnc(buyerId, sellerIdentity, secret, returnBlockHeight )
    val lc2 = LedgerContext(Map(blockHeightKey -> 10l, identityServiceKey -> IdentityService()))

    val sig = ReturnSecretDec.createUnlockingSignature(
      txId, myTag, pkPair.sign)
    intercept[Exception](enc.decumber(txId +:sig, lc2, ReturnSecretDec))
  }
}
