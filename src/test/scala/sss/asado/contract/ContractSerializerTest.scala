package sss.asado.contract

import contract.{Decumbrance, Encumbrance}
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */
class ContractSerializerTest extends FlatSpec with Matchers {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))

  "A SinglePrivateKey contract " should " be correctly serialised and deserialized " in {
    val pKeyEncumbrance = SinglePrivateKey(pkPair.publicKey)

    val bytes = ContractSerializer.toBytes(pKeyEncumbrance)
    val backAgain = ContractSerializer.fromBytes[SinglePrivateKey](bytes)

    assert(pKeyEncumbrance.pKey === backAgain.pKey)
    assert(backAgain === pKeyEncumbrance)

  }

  it should " be corrrectly serialised and deserialized as an ecumbrance " in {
    val pKeyEncumbrance = SinglePrivateKey(pkPair.publicKey)

    val bytes = ContractSerializer.toBytes[Encumbrance](pKeyEncumbrance)
    val backAgain: Encumbrance = ContractSerializer.fromBytes[Encumbrance](bytes)

    assert(backAgain === pKeyEncumbrance)

  }

  "A PrivateKeySig contract " should " be correctly serialised and deserialized " in {
    val pKeySig = PrivateKeySig

    val bytes = ContractSerializer.toBytes(pKeySig)
    val backAgain = ContractSerializer.fromBytes[PrivateKeySig.type](bytes)

    assert(backAgain === pKeySig)
  }


  it should " be correctly serialised and deserialized as a decumbrance" in {
    val pKeySig: Decumbrance = PrivateKeySig

    val bytes = ContractSerializer.toBytes(pKeySig)
    val backAgain = ContractSerializer.fromBytes[Decumbrance](bytes)

    assert(backAgain === pKeySig)
  }
}
