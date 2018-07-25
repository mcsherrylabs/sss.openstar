package sss.asado.message



import org.joda.time.{LocalDateTime, Period}
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.DummySeedBytes
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */

class MessageSpec extends FlatSpec with Matchers with ByteArrayComparisonOps {

  implicit val db = Db()

  val rndBytes = DummySeedBytes(99)
  val rndPayload = MessagePayload(100.toByte,rndBytes)
  val when = LocalDateTime.now().minus(Period.seconds(1))
  val identity = "karl"
  val lenny = "lenny"

  val tx = DummySeedBytes(12)
  val from = "from"

  "A message " should " be persistable " in {

    //val m1 = Message(identity, rndBytes, when.toDate.getTime)
    val mp = MessagePersist(identity)
    val msg = mp.pending(from, rndPayload, tx)
    mp.accept(msg)

  }


  it should " be retrievable " in {
    val retrieved = MessagePersist(identity).page(0,100)
    assert(retrieved.size == 1)
    assert(retrieved.head.msgPayload == rndPayload)
  }


  "Messages " should " be retrievable in order " in {

    val mp = MessagePersist(lenny)
    val msgs = (0 until 100) map(i => MessagePayload(i.toByte, DummySeedBytes(i)))
    msgs.map(mp.pending(from, _, tx)).map(mp.accept(_))

    val tenPages = (0 until 10) map { i =>
      mp.page(i * 10, 10)
    }
    assert(mp.page(100, 10).size == 0)
    assert(tenPages.size == 10)
    assert(!tenPages.flatten.zip(msgs).exists( {case (msg, msgPayload) => !(msg.msgPayload == msgPayload)}))

  }


  they should " be deletable " in {

    val mp = MessagePersist(lenny)
    val forDeletion = mp.page(10, 10)
    forDeletion.foreach(m => mp.delete(m.index))
    val remaining = mp.page(0, 100)
    assert(remaining.size == 90)
    val indices = forDeletion.map(_.index)
    assert(!remaining.exists(m => indices.contains(m.index)))

  }
}
