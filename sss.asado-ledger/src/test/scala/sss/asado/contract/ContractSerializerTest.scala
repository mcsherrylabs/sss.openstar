package sss.asado.contract


import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.account.PrivateKeyAccount
import sss.asado.contract.ContractSerializer._

/**
  * Created by alan on 2/15/16.
  */
class ContractSerializerTest extends FlatSpec with Matchers {


  lazy val pkPair = PrivateKeyAccount(DummySeedBytes.randomSeed(32))

  "A SinglePrivateKey contract " should " be correctly serialised and deserialized " in {
    val pKeyEncumbrance = SinglePrivateKey(pkPair.publicKey)

    val bytes = pKeyEncumbrance.toBytes
    val backAgain = SinglePrivateKeyToFromBytes.fromBytes(bytes)

    assert(pKeyEncumbrance.pKey === backAgain.pKey)
    assert(backAgain === pKeyEncumbrance)

  }

  it should " be correctly serialised and deserialized as an ecumbrance " in {
    val pKeyEncumbrance = SinglePrivateKey(pkPair.publicKey)

    val bytes = pKeyEncumbrance.toBytes
    val backAgain: Encumbrance = SinglePrivateKeyToFromBytes.fromBytes(bytes)

    assert(backAgain === pKeyEncumbrance)

  }

  "An IdentityEnc contract " should " be correctly serialised and deserialized " in {
    val pKeySig = SingleIdentityEnc("someString", 99)

    val bytes = pKeySig.toBytes
    val backAgain = bytes.toEncumbrance

    assert(backAgain === pKeySig)
  }

  "An IdentityDec contract " should " be correctly serialised and deserialized " in {
    val pKeySig: Decumbrance = SingleIdentityDec

    val bytes = pKeySig.toBytes
    val backAgain = bytes.toDecumbrance

    assert(backAgain === pKeySig)
  }

  "A PrivateKeySig contract " should " be correctly serialised and deserialized " in {
    val pKeySig = PrivateKeySig

    val bytes = pKeySig.toBytes
    val backAgain = bytes.toDecumbrance

    assert(backAgain === pKeySig)
  }


  it should " be correctly serialised and deserialized as a decumbrance" in {
    val pKeySig: Decumbrance = PrivateKeySig

    val bytes = pKeySig.toBytes
    val backAgain = bytes.toDecumbrance

    assert(backAgain === pKeySig)
  }

  "A NullDecumbrance " should " be correctly serialised and deserialized as a decumbrance" in {
    val nullDec: Decumbrance = NullDecumbrance

    val bytes = nullDec.toBytes
    val backAgain = bytes.toDecumbrance

    assert(backAgain === nullDec)
  }

  "A NullEcumbrance " should " be correctly serialised and deserialized as an ecumbrance" in {
    val nullEnc: Encumbrance = NullEncumbrance

    val bytes = nullEnc.toBytes
    val backAgain = bytes.toEncumbrance

    assert(backAgain === nullEnc)
  }

  "A SaleOrReturnSecretEnc  " should " be correctly serialised and deserialized as an ecumbrance" in {
    val sellerIdentity = "sellerIdentity"
    val buyerId = "buyerId"
    val secret = "ohnowhathavetheydone"
    val hashOfSecret = (0 to 10). map (_.toByte).toArray
    val returnBlockHeight = 10

    val enc = SaleOrReturnSecretEnc(buyerId, sellerIdentity, hashOfSecret, returnBlockHeight )
    val bytes = enc.toBytes
    val backAgain = bytes.toEncumbrance

    assert(backAgain === enc)
  }
}
