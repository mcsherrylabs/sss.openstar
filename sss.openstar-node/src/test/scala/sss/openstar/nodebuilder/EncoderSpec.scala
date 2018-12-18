package sss.openstar.nodebuilder


import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.{DummySeedBytes, MessageKeys, Send}
import sss.openstar.block.GetTxPage
import sss.openstar.eventbus.StringMessage
import sss.openstar.ledger._

import sss.openstar.message.EndMessageQuery
import sss.openstar.network.SerializedMessage
import sss.openstar.util.ByteArrayComparisonOps


/**
  * Created by alan on 2/15/16.
  */

class EncoderSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  val sut = Send.ToSerializedMessageImpl

  implicit val chainId = 0.toByte

  "A Encoder " should " prevent an encoding without corresponding object" in {

    intercept[AssertionError](
      sut(MessageKeys.GetPageTx)
    )

  }

  it should "allow encoding and decoding " in {
    val byte = MessageKeys.GetPageTx
    val test = GetTxPage(1,2,3)
    val SerializedMessage(`chainId`, `byte`, data) = sut(MessageKeys.GetPageTx, test)
    import sss.openstar.block._
    val backAgain = data.toGetTxPage
    assert(backAgain === test)
  }

  it should "prevent encoding the wrong object " in {

    intercept[AssertionError](
      sut(MessageKeys.BlockSig, GetTxPage(1,2,3))
    )

  }

  it should "work with String messages" in {
    val sm = StringMessage("Some string ")
    val sMsg = sut(MessageKeys.MalformedMessage, sm)
    val backAgain = MessageKeys.messages.find(MessageKeys.MalformedMessage).get.fromBytes(sMsg.data)
    assert(backAgain === sm)
  }

  it should "be able to encode a Seq of objects" in {

    val l1 = LedgerItem(DummySeedBytes(1).head, DummySeedBytes(32), DummySeedBytes(3))
    val l2 = LedgerItem(DummySeedBytes(1).head, DummySeedBytes(32), DummySeedBytes(3))
    assert(l1 === l1)
    assert(l2 === l2)
    val seqStx: Seq[LedgerItem] = Seq(l1,l2)
    val sMsg = sut(MessageKeys.SeqSignedTx, SeqLedgerItem(seqStx))
    val backAgain = MessageKeys.messages.find(MessageKeys.SeqSignedTx).get.fromBytes(sMsg.data)
    assert(SeqLedgerItem(seqStx) === backAgain)

  }

}
