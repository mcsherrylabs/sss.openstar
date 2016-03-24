package sss.asado.block.serialize

import block._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.PrivateKeyAccount
import sss.asado.ledger.serialize.SignedTxTest
import sss.asado.util.SeedBytes

/**
  * Created by alan on 2/15/16.
  */
class BlockSerializerTest extends FlatSpec with Matchers {


  lazy val pkPair = PrivateKeyAccount(SeedBytes(32))

  val height = 33
  val id = 20000
  val stx = SignedTxTest.createSignedTx

  "A Confirm Tx " should " be correctly serialised and deserialized " in {
    val c = ConfirmTx(stx, height, id)
    val asBytes = c.toBytes
    val backAgain = asBytes.toConfirmTx
    assert(backAgain === c)

  }

  "An Ack Confirm Tx" should " be corrrectly serialised and deserialized as an ecumbrance " in {
    val c = AckConfirmTx(stx.txId, height, id)
    val asBytes = c.toBytes
    val backAgain = asBytes.toAckConfirmTx
    assert(backAgain.height === c.height)
    assert(backAgain.id === c.id)
    assert(backAgain.txId === c.txId)
    assert(backAgain === c)

  }

}
