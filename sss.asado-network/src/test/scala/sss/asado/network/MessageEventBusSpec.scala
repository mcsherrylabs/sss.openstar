package sss.asado.network

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.ByteString

import scala.language.postfixOps
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.AsadoEvent

import concurrent.duration._
import sss.asado.network.MessageEventBus.HasNodeId

class MessageEventBusSpec extends FlatSpec with Matchers {

  import TestActorSystem._

  val probe1 = TestProbe()

  val probe2 = TestProbe()
  val observer1 = probe1.ref
  val observer2 = probe2.ref

  val fromNodeId: NodeId =
    NodeId("test", InetSocketAddress.createUnresolved("somehost.com", 8008))
  val connController = TestProbe().ref
  val incomingNetworkMessage =
    IncomingNetworkMessage(fromNodeId, 1.toByte, Array())

  val networkMessageAsApplicationTestMessage =
    TestMessage(fromNodeId, ByteString())

  val myEvent = MyEvent(9)
  val myOtherEvent = MyOtherEvent(10)

  "EventBus " should " receive raw msg once subsrcibed " in {

    msgBus.subscribe(1.toByte)( observer1)
    msgBus.publish(incomingNetworkMessage)

    probe1.expectMsg(incomingNetworkMessage)
  }

  it should " not receive msg when unsubscribed" in {
    msgBus.unsubscribe(1.toByte)( observer1)
    msgBus.publish(incomingNetworkMessage)
    probe1.expectNoMessage(1 second)
  }

  it should " allow a second observer to receive " in {
    msgBus.subscribe(1.toByte)( observer2)
    msgBus.publish(incomingNetworkMessage)

    probe1.expectNoMessage(1 second)
    probe2.expectMsg(incomingNetworkMessage)
  }

  it should " support receiving a decoded network message " in {
    msgBus.subscribe(classOf[TestMessage])( observer2)
    msgBus.publish(incomingNetworkMessage)

    probe1.expectNoMessage(1 second)
    probe2.expectMsg(incomingNetworkMessage)
    probe2.expectMsg(networkMessageAsApplicationTestMessage)
  }

  it should " support unsubscribing from all " in {
    msgBus.subscribe(1.toByte)( observer1)
    msgBus.unsubscribe(observer2)
    msgBus.publish(incomingNetworkMessage)

    probe1.expectMsg(incomingNetworkMessage)
    probe2.expectNoMessage(1 second)
  }

  it should " publishing an Asado Event" in {

    msgBus.subscribe(classOf[MyEvent])( observer1)
    msgBus.publish(myEvent)
    probe1.expectMsg(myEvent)

  }

  it should " support registering for sub classification of events " in {

    msgBus.unsubscribe(classOf[MyEvent])( observer1)
    msgBus.subscribe(classOf[AsadoEvent])( observer1) // base class of both events
    msgBus.publish(myEvent)
    msgBus.publish(myOtherEvent)
    probe1.expectMsg(myEvent)
    probe1.expectMsg(myOtherEvent)

  }

  it should " support registering for sub classification of network messages" in {

    msgBus.unsubscribe(observer1)
    msgBus.subscribe(classOf[HasNodeId])( observer1)
    msgBus.publish(incomingNetworkMessage)
    probe1.expectMsg(networkMessageAsApplicationTestMessage)

  }

}
