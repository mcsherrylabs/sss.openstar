package sss.asado.nodebuilder


import org.scalatest.{FlatSpec, Matchers}
import sss.asado.{DummySeedBytes, MessageKeys}
import sss.asado.block.GetTxPage
import sss.asado.eventbus.StringMessage
import sss.asado.ledger.LedgerItem
import sss.asado.util.SeqSerializer.SeqToBytes
import sss.asado.message.EndMessageQuery
import sss.asado.network.SerializedMessage
import sss.asado.util.ByteArrayComparisonOps


/**
  * Created by alan on 2/15/16.
  */

class EncoderSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {


  object sut extends EncoderBuilder with RequireGlobalChainId
  import sut.globalChainId

  val chainId = sut.globalChainId

  "A Encoder " should " prevent an encoding without corresponding object" in {

    intercept[AssertionError](
      sut.encode(MessageKeys.GetPageTx)
    )

  }

  it should "allow encoding and decoding " in {
    val byte = MessageKeys.GetPageTx
    val test = GetTxPage(1,2,3)
    val SerializedMessage(`chainId`, `byte`, data) = sut.encode(MessageKeys.GetPageTx, test)
    import sss.asado.block._
    val backAgain = data.toGetTxPage
    assert(backAgain === test)
  }

  it should "prevent encoding the wrong object " in {

    intercept[AssertionError](
      sut.encode(MessageKeys.BlockSig, GetTxPage(1,2,3))
    )

  }

  it should "allow encoding case objects " in {

    val sMsg = sut.encode(MessageKeys.EndMessageQuery, EndMessageQuery)
    assert(sMsg.data === Array())
    assert(sMsg.chainId === chainId)
    assert(sMsg.msgCode === MessageKeys.EndMessageQuery)
  }

  it should "work with String messages" in {
    val sm = StringMessage("Some string ")
    val sMsg = sut.encode(MessageKeys.MalformedMessage, sm)
    val backAgain = MessageKeys.messages.find(MessageKeys.MalformedMessage).get.fromBytes(sMsg.data)
    assert(backAgain === sm)
  }

  it should "be able to encode a Seq of objects" in {

    val l1 = LedgerItem(DummySeedBytes(1).head, DummySeedBytes(32), DummySeedBytes(3))
    val l2 = LedgerItem(DummySeedBytes(1).head, DummySeedBytes(32), DummySeedBytes(3))
    assert(l1 === l1)
    assert(l2 === l2)
    val seqStx: Seq[LedgerItem] = Seq(l1,l2)
    val sMsg = sut.encode(MessageKeys.SeqSignedTx, seqStx)
    val backAgain = MessageKeys.messages.find(MessageKeys.SeqSignedTx).get.fromBytes(sMsg.data)
    assert(seqStx === backAgain)

  }

}
