package sss.asado.network

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.util.ByteString
import sss.asado.eventbus.MessageInfo
import sss.asado.{AsadoEvent, UniqueNodeIdentifier}


object TestActorSystem {

  implicit val actorSystem: ActorSystem = ActorSystem()

  case class MyEvent(i: Int) extends AsadoEvent
  case class MyOtherEvent(i: Int) extends AsadoEvent

  trait SuperClass
  case class TestMessage(

                         data: ByteString) extends SuperClass


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
