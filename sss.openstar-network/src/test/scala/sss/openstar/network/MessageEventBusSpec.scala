package sss.openstar.network


import akka.testkit.TestProbe
import akka.util.ByteString

import scala.language.postfixOps
import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.OpenstarEvent
import sss.openstar.network.MessageEventBus.{IncomingMessage, UnsubscribedEvent, UnsubscribedIncomingMessage}

import concurrent.duration._

class MessageEventBusSpec extends FlatSpec with Matchers {

  import TestActorSystem._

  val probe1 = TestProbe()

  val probe2 = TestProbe()
  val observer1 = probe1.ref
  val observer2 = probe2.ref

  val fromNodeId = "test"
    //NodeId("test", InetSocketAddress.createUnresolved("somehost.com", 8008))
  val connController = TestProbe().ref
  val incomingSerializedNetworkMessage =
    IncomingSerializedMessage( fromNodeId, SerializedMessage(1.toByte, 1.toByte, Array()))

  val networkMessageAsApplicationTestMessage =
    TestMessage(ByteString())

  val incomingNetworkMessage =
    IncomingMessage(1.toByte, 1, fromNodeId, networkMessageAsApplicationTestMessage)

  val myEvent = MyEvent(9)
  val myOtherEvent = MyOtherEvent(10)

  "EventBus " should " receive raw msg once subsrcibed " in {

    msgBus.subscribe(1.toByte)( observer1)
    msgBus.publish(incomingSerializedNetworkMessage)

    probe1.expectMsg(incomingNetworkMessage)
  }

  it should " prevent subscribing to unknown msgCode " in {

    intercept[IllegalArgumentException] {
      msgBus.subscribe(2.toByte)(observer1)
    }
  }

  it should " not receive msg when unsubscribed" in {
    msgBus.unsubscribe(1.toByte)( observer1)
    msgBus.publish(incomingSerializedNetworkMessage)
    probe1.expectNoMessage(1 second)
  }

  it should " allow a second observer to receive " in {
    msgBus.subscribe(1.toByte)( observer2)
    msgBus.publish(incomingSerializedNetworkMessage)

    probe1.expectNoMessage(1 second)
    probe2.expectMsg(incomingNetworkMessage)
  }


  it should " support unsubscribing from all " in {
    msgBus.subscribe(1.toByte)( observer1)
    msgBus.unsubscribe(observer2)
    msgBus.publish(incomingSerializedNetworkMessage)

    probe1.expectMsg(incomingNetworkMessage)
    probe2.expectNoMessage(1 second)
  }

  it should " publishing an Openstar Event" in {

    msgBus.subscribe(classOf[MyEvent])( observer1)
    msgBus.publish(myEvent)
    probe1.expectMsg(myEvent)

  }

  it should " support registering for sub classification of events " in {

    msgBus.unsubscribe(classOf[MyEvent])( observer1)
    msgBus.subscribe(classOf[OpenstarEvent])( observer1) // base class of both events
    msgBus.publish(myEvent)
    msgBus.publish(myOtherEvent)
    probe1.expectMsg(myEvent)
    probe1.expectMsg(myOtherEvent)

  }

  it should " support registering for sub classification of network messages" in {

    msgBus.unsubscribe(observer1)
    msgBus.subscribe(classOf[SuperClass])( observer1)
    msgBus.publish(myEvent)
    probe1.expectMsg(myEvent)

  }

  it should " support registering for unsubscribed net messages " in {

    //No net message when subscribed to Events
    msgBus.unsubscribe(observer1)
    msgBus.subscribe(classOf[UnsubscribedEvent[_]])( observer1)
    msgBus.publish(incomingSerializedNetworkMessage)
    probe1.expectNoMessage(1 second)

    //Subscribe to net message, get net message
    msgBus.subscribe(classOf[UnsubscribedIncomingMessage])( observer1)
    msgBus.publish(incomingSerializedNetworkMessage)
    probe1.expectMsg(UnsubscribedIncomingMessage(incomingNetworkMessage))

    //check unsubscribe works
    msgBus.unsubscribe(classOf[UnsubscribedIncomingMessage])(observer1)
    msgBus.publish(incomingSerializedNetworkMessage)
    probe1.expectNoMessage(1 second)


  }

  it should " support registering for unsubscribed events " in {

    msgBus.unsubscribe(observer1)
    msgBus.subscribe(classOf[UnsubscribedEvent[_]])( observer1)
    msgBus.publish(myEvent)
    probe1.expectMsg(UnsubscribedEvent(myEvent))

  }
}
