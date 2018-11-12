package sss.asado.network

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import sss.ancillary.Logging
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.{AsadoEvent, UniqueNodeIdentifier}
import sss.asado.eventbus.{EventPublish, MessageInfo}
import sss.asado.network.MessageEventBus._

import scala.reflect.ClassTag

object MessageEventBus {

  trait  Unsubscribed
  case class UnsubscribedIncomingMessage(msg: IncomingMessage[_]) extends Unsubscribed
  case class UnsubscribedEvent[T <: AsadoEvent](event: T) extends Unsubscribed

  case class IncomingMessage[T](chainCode: GlobalChainIdMask, code: Byte, nodeId: UniqueNodeIdentifier, msg: T)

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
          //msgBus unsubscribe newRef
        }

      case IncWatch(newRef) =>
        context watch newRef
        refs += newRef -> (refs(newRef) + 1)
    }
  }

  trait NetworkMessagePublish {
    private[network] def publish(networkMessage: IncomingSerializedMessage)
  }


  trait EventSubscriptions {

    def unsubscribe(ref: ActorRef)
    def unsubscribe(msgCode: Byte)(implicit ref: ActorRef)
    def unsubscribe(clazz: Class[_])(implicit ref: ActorRef)
    def subscribe(msgCode: Byte)(implicit ref: ActorRef)
    def subscribe(clazz: Class[_])(implicit ref: ActorRef)

  }

  implicit class EventSubscriptionsClassOps(val seq :Seq[Class[_]])(implicit bus:EventSubscriptions) {
    def subscribe(implicit ref: ActorRef): Unit = {
      seq foreach (bus.subscribe(_))
    }

    def unsubscribe(implicit ref: ActorRef): Unit = {
      seq foreach (bus.unsubscribe(_))
    }
  }

  implicit class EventSubscriptionsByteOps(val seq :Seq[Byte])(implicit bus:EventSubscriptions) {
    def subscribe(implicit ref: ActorRef): Unit = {
      seq foreach (bus.subscribe(_))
    }

    def unsubscribe(implicit ref: ActorRef): Unit = {
      seq foreach (bus.unsubscribe(_))
    }
  }
}


class MessageEventBus (decoder: Byte => Option[MessageInfo], loggingSuppressedClasses: Seq[Class[_]] = Seq.empty)(
    implicit actorSystem: ActorSystem)
    extends NetworkMessagePublish
    with EventPublish
    with EventSubscriptions
    with Logging {

  private val trackRefs =
    actorSystem.actorOf(Props(classOf[TrackSubscribedRefs], this))

  private val unsubscribedHandlers :AtomicReference[MapToRefs[Class[_]]] =
    new AtomicReference[MapToRefs[Class[_]]](Map() withDefaultValue Set())

  private val msgCodeSubscriptions: AtomicReference[MapToRefs[Byte]] =
    new AtomicReference(Map().withDefaultValue(Set()))

  private val msgClassSubscriptions: AtomicReference[MapToRefs[Class[_]]] =
    new AtomicReference(Map().withDefaultValue(Set()))

  type MapToRefs[E] = Map[E, Set[ActorRef]]

  def shutdown() = trackRefs ! PoisonPill

  private def removeRefFromMap[E](refToRemove: ActorRef)(
      acc: MapToRefs[E],
      e: (E, Set[ActorRef])): MapToRefs[E] = {

    log.whenDebugEnabled(
      e._2.find(_ == refToRemove)
      .map(found => log.debug(s"Unsubscribe Ref - ${found.path.name} unsubscribed from ${e._1}"))
    )

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

    unsubscribedHandlers.updateAndGet(
      handlers =>
        handlers
          .foldLeft(Map() withDefaultValue (Set()): MapToRefs[Class[_]])(
            removeRefFromMap(ref))
    )
  }

  private def unsubscribeImpl[E](e: E,
                                 ref: ActorRef,
                                 map: MapToRefs[E]): MapToRefs[E] = {

    val refList = map(e)
    val newList = refList - ref

    log.whenDebugEnabled ({
      val wasSubscribed = if (refList.contains(ref)) "" else "again"
      log.debug(s"${ref.path.name} unsubscribes from $e $wasSubscribed")
    })

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

    if(classOf[Unsubscribed].isAssignableFrom(clazz)) {
      unsubscribedHandlers.updateAndGet(
        handlers => unsubscribeImpl(clazz, ref, handlers)
      )
    }
  }

  private def subscribeImpl[E](e: E,
                               ref: ActorRef,
                               map: MapToRefs[E]): MapToRefs[E] = {
    val setOfRefs = map(e)
    val newSetOfRefs = setOfRefs + ref
    log.whenDebugEnabled ({
      val wasSubscribed = if (setOfRefs.contains(ref)) "again" else ""
      log.debug(s"${ref.path.name} subscribes to $e $wasSubscribed")
    })
    map + (e -> newSetOfRefs)
  }

  def subscribe(msgCode: Byte)(implicit ref: ActorRef): Unit = {

    require(decoder(msgCode).isDefined, s"Cannot subscribe for unknown msgCode $msgCode")

    trackRefs ! IncWatch(ref)

    msgCodeSubscriptions.updateAndGet(
      msgCodeSubs => subscribeImpl[Byte](msgCode, ref, msgCodeSubs)
    )
  }

  override def subscribe(clazz: Class[_])(implicit ref: ActorRef): Unit = {

    trackRefs ! IncWatch(ref)

    if(classOf[Unsubscribed].isAssignableFrom(clazz)) {
      unsubscribedHandlers.updateAndGet(
        unsubscribed => subscribeImpl[Class[_]](clazz, ref, unsubscribed)
      )
    } else {
      msgClassSubscriptions.updateAndGet(
        msgClassSubs => subscribeImpl[Class[_]](clazz, ref, msgClassSubs)
      )
    }
  }

  override def publish[T <: AsadoEvent: ClassTag](event: T): Unit = {
    val clazz = event.getClass()

    val subs = msgClassSubscriptions.get()


    val eventWasFired = subs.foldLeft[Boolean](false) {
      case (acc: Boolean, (k: Class[_], subs: Set[ActorRef])) if k.isAssignableFrom(clazz) =>

        log.whenDebugEnabled({
          if (!loggingSuppressedClasses.exists(_.isAssignableFrom(clazz)))
            subs.foreach(sub => log.debug(s"$event -> ${sub.path.name}"))
        })

        subs foreach (_ ! event)
        acc | subs.nonEmpty
      case (acc: Boolean, e) => acc
    }

    if(!eventWasFired) {
      unsubscribedHandlers.get() foreach {
        case (k,v) =>
          if (k.isAssignableFrom(classOf[UnsubscribedEvent[_]]))
            v foreach (_ ! UnsubscribedEvent(event))
      }
    }
  }

  private[network] override def publish(networkMessage: IncomingSerializedMessage): Unit = {
    val msgClassSubs = msgClassSubscriptions.get()
    val msgCodeSubs = msgCodeSubscriptions.get()

    val msgCode = networkMessage.msg.msgCode
    val chainCode = networkMessage.msg.chainId

    decoder(msgCode) match {

      case None =>
        log.warn(s"No decoder found for $msgCode.")

      case Some(info) =>

        lazy val incomingMessage = {
          val msg = info.fromBytes(networkMessage.msg.data)
          IncomingMessage(chainCode, msgCode, networkMessage.fromNodeId, msg)
        }

        val subs = msgCodeSubs(msgCode)

        log.whenDebugEnabled {
          log.debug(s"IncomingMessage: ${incomingMessage.msg}")
          subs.foreach(sub => log.debug(s"Chain:${incomingMessage.chainCode} " +
            s"(${incomingMessage.code} " +
            s"${info.clazz.getSimpleName.padTo(20, ' ')}) " +
            s"from:${incomingMessage.nodeId} -> ${sub.path.name}"))
        }

        subs foreach (_ ! incomingMessage)

        /*val eventWasFired = msgClassSubs.foldLeft[Boolean](false) {
          case (acc: Boolean, (k: Class[_], subs: Set[ActorRef])) if k.isAssignableFrom(info.clazz) =>


            log.whenDebugEnabled({
              subs.foreach(sub => log.debug(s"${incomingMessage.msg} -> ${sub.path.name}"))
            })

            subs foreach (_ ! incomingMessage.msg)
            acc | subs.nonEmpty
          case (acc: Boolean, e) => acc
        }*/

        if(subs.isEmpty /*&& !eventWasFired*/) {
          unsubscribedHandlers.get() foreach {
            case (k,v) =>
              if (k.isAssignableFrom(classOf[UnsubscribedIncomingMessage]))
                v foreach (_ ! UnsubscribedIncomingMessage(incomingMessage))

          }
        }

    }
  }
}
