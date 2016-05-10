package sss.asado.contract

import contract.{NullDecumbrance, NullEncumbrance}
import ledger.{GenisesTx, SignedTx, TxOutput}
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.util.{ByteArrayComparisonOps, SeedBytes}

/**
  * Created by alan on 2/15/16.
  */
class ContractTest extends FlatSpec with Matchers with ByteArrayComparisonOps {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))

  "A single sig " should " unlock a single key contract " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(enc.decumber(Seq(msg.getBytes(), sig), PrivateKeySig))
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

    assert(!enc.decumber(Seq((msg.getBytes()), sig), NullDecumbrance))
  }

  it  should " fail if the msg is different  " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(!enc.decumber(Seq((msg.getBytes() ++ Array[Byte](0)), sig), PrivateKeySig))
  }

  it  should " fail if the sig is different  " in {

    val enc = SinglePrivateKey(pkPair.publicKey)
    assert(enc.pKey.isSame(pkPair.publicKey))

    val msg = "sfsfsdfsdfsdf"
    val sig = pkPair.sign(msg.getBytes)

    assert(!enc.decumber(Seq((msg.getBytes()), sig ++ Array[Byte](0)), PrivateKeySig))
  }

  "A null ecumbrance" should " be decumbered by Null decumbrance " in {
    assert(NullEncumbrance.decumber(Seq(), NullDecumbrance))
  }

  it should "  be decumbered by any decumbrance " in {
    assert(NullEncumbrance.decumber(Seq(), PrivateKeySig))
  }
}
