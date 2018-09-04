package sss.asado.network

import akka.actor.{ActorRef, ActorSystem}
import akka.util.ByteString
import sss.asado.AsadoEvent
import sss.asado.network.MessageEventBus.{HasNodeId, MessageInfo}

object TestActorSystem {

  implicit val actorSystem: ActorSystem = ActorSystem()

  case class MyEvent(i: Int) extends AsadoEvent
  case class MyOtherEvent(i: Int) extends AsadoEvent

  case class TestMessage(
                         nodeId: UniqueNodeIdentifier,
                         data: ByteString)
      extends HasNodeId


  object TestMessageInfo extends MessageInfo {

    override val msgCode: Byte = 1
    override type T = TestMessage
    override val clazz: Class[T] = classOf[T]

    override def fromBytes(nodeId: UniqueNodeIdentifier, bytes: Array[Byte]) =
      TestMessage(nodeId, ByteString(bytes))
  }

  val messages = Set(TestMessageInfo)
  val decoder: Byte => Option[MessageInfo] =
    messages.map(b => b.msgCode -> b).toMap.get

  lazy val msgBus = new MessageEventBus(decoder)
  lazy val msgBus2 = new MessageEventBus(decoder)
}
