package sss.asado.network

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import sss.ancillary.Logging
import sss.asado.AsadoEvent
import sss.asado.network.MessageEventBus._

import scala.reflect.ClassTag

object MessageEventBus {

  private case class UnWatch(ref: ActorRef)
  private case class DecWatch(ref: ActorRef)
  private case class IncWatch(ref: ActorRef)

  private class TrackSubscribedRefs(msgBus: MessageEventBus) extends Actor {

    private var refs = Map[ActorRef, Int]().withDefaultValue(0)

    def receive = {
      case UnWatch(ref) =>
        context unwatch ref
        refs -= ref

      case Terminated(ref) =>
        msgBus unsubscribe ref
        refs -= ref

      case DecWatch(newRef) =>
        val currReferenceCount = refs(newRef)
        val newCount = currReferenceCount - 1
        if (newCount > 0)
          refs += newRef -> (newCount)
        else if (newCount <= 0) {
          context unwatch newRef
          refs -= newRef
          msgBus unsubscribe newRef
        }

      case IncWatch(newRef) =>
        context watch newRef
        refs += newRef -> (refs(newRef) + 1)
    }
  }

  trait HasNodeId {
    val nodeId: NodeId
  }


  trait MessageInfo {
    val msgCode: Byte
    type T <: HasNodeId
    val clazz: Class[T]
    def fromBytes(nodeId: NodeId, bytes: Array[Byte]): T
  }

  trait NetworkMessagePublish {
    def publish(networkMessage: IncomingNetworkMessage)
    //todo call this serialised message and
    // todo put it back in Incoming
    def publish(networkMessage: NetworkMessage)
  }

  trait EventPublish {
    def publish[T <: AsadoEvent: ClassTag](event: T): Unit
  }

  trait EventSubscriptions {

    def unsubscribe(ref: ActorRef)
    def unsubscribe(msgCode: Byte)(implicit ref: ActorRef)
    def unsubscribe(clazz: Class[_])(implicit ref: ActorRef)
    def subscribe(msgCode: Byte)(implicit ref: ActorRef)
    def subscribe(clazz: Class[_])(implicit ref: ActorRef)

  }
}

class MessageEventBus(decoder: Byte => Option[MessageInfo])(
    implicit actorSystem: ActorSystem)
    extends NetworkMessagePublish
    with EventPublish
    with EventSubscriptions
    with Logging {

  private val trackRefs =
    actorSystem.actorOf(Props(classOf[TrackSubscribedRefs], this))

  private val msgCodeSubscriptions: AtomicReference[MapToRefs[Byte]] =
    new AtomicReference(Map().withDefaultValue(Set()))

  private val msgClassSubscriptions: AtomicReference[MapToRefs[Class[_]]] =
    new AtomicReference(Map().withDefaultValue(Set()))

  type MapToRefs[E] = Map[E, Set[ActorRef]]

  def shutdown() = trackRefs ! PoisonPill

  private def removeRefFromMap[E](refToRemove: ActorRef)(
      acc: MapToRefs[E],
      e: (E, Set[ActorRef])): MapToRefs[E] = {

    acc + (e._1 -> e._2.filterNot(_ == refToRemove))

  }

  override def unsubscribe(ref: ActorRef) {

    trackRefs ! UnWatch(ref)

    msgCodeSubscriptions.updateAndGet(
      msgCodeSubs =>
        msgCodeSubs
          .foldLeft(Map() withDefaultValue (Set()): MapToRefs[Byte])(
            removeRefFromMap(ref))
    )

    msgClassSubscriptions.updateAndGet(
      msgClassSubs =>
        msgClassSubs
          .foldLeft(Map() withDefaultValue (Set()): MapToRefs[Class[_]])(
            removeRefFromMap(ref))
    )

  }

  private def unsubscribeImpl[E](e: E,
                                 ref: ActorRef,
                                 map: MapToRefs[E]): MapToRefs[E] = {

    val refList = map(e)
    val newList = refList.filterNot(_ == ref)
    map + (e -> newList)

  }

  override def unsubscribe(msgCode: Byte)(implicit ref: ActorRef): Unit = {

    trackRefs ! DecWatch(ref)

    msgCodeSubscriptions.updateAndGet(
      msgCodeSubs => unsubscribeImpl(msgCode, ref, msgCodeSubs)
    )
  }

  def unsubscribe(clazz: Class[_])(implicit ref: ActorRef): Unit = {

    trackRefs ! DecWatch(ref)

    msgClassSubscriptions.updateAndGet(
      msgClassSubs => unsubscribeImpl(clazz, ref, msgClassSubs)
    )
  }

  private def subscribeImpl[E](msgCode: E,
                               ref: ActorRef,
                               map: MapToRefs[E]): MapToRefs[E] = {
    val setOfRefs = map(msgCode)
    val newSetOfRefs = setOfRefs + ref
    map + (msgCode -> newSetOfRefs)
  }

  def subscribe(msgCode: Byte)(implicit ref: ActorRef): Unit = {

    trackRefs ! IncWatch(ref)

    msgCodeSubscriptions.updateAndGet(
      msgCodeSubs => subscribeImpl[Byte](msgCode, ref, msgCodeSubs)
    )
  }

  override def subscribe(clazz: Class[_])(implicit ref: ActorRef): Unit = {

    trackRefs ! IncWatch(ref)

    msgClassSubscriptions.updateAndGet(
      msgClassSubs => subscribeImpl[Class[_]](clazz, ref, msgClassSubs)
    )
  }

  override def publish[T <: AsadoEvent: ClassTag](event: T): Unit = {
    val clazz = implicitly[ClassTag[T]].runtimeClass

    val subs = msgClassSubscriptions.get()

    /*log.whenDebugEnabled(
      subs.foreach { sub =>
        log.debug(s"Ref: ${sub._1} to ... ")
        sub._2 foreach { c =>
          log.debug(c.toString())
        }
        log.debug(s"-----------------")
      }
    )*/

    subs.foreach {
      case (k, v) if k.isAssignableFrom(clazz) =>
        v.map { r =>
          //log.debug(s"Sending $event of type $clazz to $r because of $k")
          r
        }.foreach (_ ! event)
      case _ => ()
    }
  }

  override def publish(networkMessage: NetworkMessage): Unit = {
    publish(IncomingNetworkMessage(NodeId("dummy",
      InetSocketAddress.createUnresolved("localhost", 8888)),
      networkMessage.msgCode,
      networkMessage.data)
    )
  }

  override def publish(networkMessage: IncomingNetworkMessage): Unit = {
    val msgClassSubs = msgClassSubscriptions.get()
    val msgCodeSubs = msgCodeSubscriptions.get()

    val msgCode = networkMessage.msgCode

    msgCodeSubs(msgCode) foreach (_ ! networkMessage)

    decoder(msgCode) match {
      case None =>
        log.warn(s"No decoder found for $msgCode.")
      case Some(info) =>
        msgClassSubs foreach {
          case (k, subs) if k.isAssignableFrom(info.clazz) =>
            if (!subs.isEmpty) {
              val msg = info.fromBytes(
                                       networkMessage.fromNodeId,
                                       networkMessage.data)

              subs foreach (_ ! msg)
            }
          case _ => ()
        }
        val subs = msgClassSubs(info.clazz)

    }

  }
}
