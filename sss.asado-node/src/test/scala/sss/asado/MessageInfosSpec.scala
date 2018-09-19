package sss.asado

import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.block.VoteLeader
import sss.asado.eventbus.PureEvent
import sss.asado.message.EndMessageQuery


/**
  * Created by alan on 2/15/16.
  */

class MessageInfosSpec extends FlatSpec with Matchers  {

  val someIdentifier = "someIdentifier"
  val h = 56
  val i = 67
  val vl = VoteLeader(someIdentifier, h,i)
  val vlBytes = vl.toBytes

  "A VoteLeader message info " should " be searchable " in {

    val info = MessageKeys.messages.find(MessageKeys.VoteLeader)
    assert(info.isDefined, "Couldnt find vote leader in messages")
  }

  "A non existent message info " should " be not found " in {

    val info = MessageKeys.messages.find(Byte.MaxValue)
    assert(info.isEmpty, "Found Byre.MaxValue in messages, are ALL bytes used?")
  }

  "A messageInfo " should " correspond to correct class " in {

    val info = MessageKeys.messages.find(MessageKeys.VoteLeader)
    val vlBackAgain = info.map(_.fromBytes(vlBytes)).get
    assert(vl === vlBackAgain, "Serialized not same as deserialised")
  }

  "A messageInfo for a pure event " should " be found " in {

    val info = MessageKeys.messages.find(MessageKeys.SimpleEndPageTx)
    val found = info.get
    val event = found.fromBytes(Array())
    assert(event === PureEvent(MessageKeys.SimpleEndPageTx), "Serialized not same as deserialised")
  }

  "A messageInfo for string event " should " be found " in {

    val info = MessageKeys.messages.find(MessageKeys.MalformedMessage)
    val deserialized = info.map(_.fromBytes("Hello from the otherside".getBytes(StandardCharsets.UTF_8))).get
    assert("Hello from the otherside" === deserialized, "Serialized not same as deserialised")
  }

  "A case object event " should " be returned " in {

    val info = MessageKeys.messages.find(MessageKeys.EndMessageQuery)
    val found = info.get
    val deserialized = found.fromBytes(Array())
    assert(EndMessageQuery === deserialized, "Serialized not same as deserialised")
  }

  "Messages " should "preserve their type " in {
    MessageKeys.messages.find(MessageKeys.VoteLeader).get.fromBytes(vlBytes) match {
      case VoteLeader(`someIdentifier`, `h`, `i`) =>
      case x => fail("did not match on VoteLeader")
    }
  }
}
