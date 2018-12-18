package sss.openstar.network

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.util.ByteString
import sss.openstar.eventbus.MessageInfo
import sss.openstar.network.TestActorSystem.SuperClass
import sss.openstar.{OpenstarEvent, UniqueNodeIdentifier}


object TestActorSystem {

  implicit val actorSystem: ActorSystem = ActorSystem()

  case class MyEvent(i: Int) extends OpenstarEvent with SuperClass
  case class MyOtherEvent(i: Int) extends OpenstarEvent

  trait SuperClass
  case class TestMessage(

                         data: ByteString)


  object TestMessageInfo extends MessageInfo {

    override val msgCode: Byte = 1
    override type T = TestMessage
    override val clazz: Class[T] = classOf[T]

    override def fromBytes(bytes: Array[Byte]) =
      TestMessage(ByteString(bytes))
  }

  val messages = Set(TestMessageInfo)
  val decoder: Byte => Option[MessageInfo] =
    messages.map(b => b.msgCode -> b).toMap.get

  object LogActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case x => log.info(s"Testing default message handler   $x")
    }
  }

  lazy val msgBus = new MessageEventBus(decoder)
  lazy val msgBus2 = new MessageEventBus(decoder)
}
