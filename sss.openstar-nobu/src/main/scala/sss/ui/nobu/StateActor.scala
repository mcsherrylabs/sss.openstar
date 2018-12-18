package sss.ui.nobu

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import sss.openstar.block.{NotSynchronized, Synchronized}
import sss.openstar.{OpenstarEvent, QueryStatus, Status}
import sss.ui.nobu.Main.ClientNode
import sss.ui.nobu.StateActor.StateQueryStatus

/**
  * Created by alan on 11/9/16.
  */
object StateActor {

  case object StateQueryStatus extends OpenstarEvent

  def apply(clientNode: ClientNode)(implicit as: ActorSystem): ActorRef = {
    as.actorOf(Props(classOf[StateActor], clientNode), "StateActor")
  }

  case object NoState
}

class StateActor(clientNode: ClientNode) extends Actor {

  import clientNode._
  val chainId = clientNode.chain.id

  messageEventBus.subscribe(
    StateQueryStatus.getClass
      .asInstanceOf[Class[StateQueryStatus.type]]) // REALLY?
  messageEventBus.subscribe(classOf[Synchronized])
  messageEventBus.subscribe(classOf[NotSynchronized])

  private def handleStateStatus: Receive = {

    case StateQueryStatus =>
      self ! QueryStatus

  }

  override def receive: Receive = notSynced(NotSynchronized(chainId))

  private def notSynced(ns: NotSynchronized): Receive =
    handleStateStatus orElse {
      case QueryStatus =>
        messageEventBus publish Status(ns)

      case s: Synchronized =>
        context become synced(s)
    }

  private def synced(s: Synchronized): Receive = handleStateStatus orElse {
    case QueryStatus =>
      messageEventBus publish Status(s)

    case ns: NotSynchronized =>
      context become notSynced(ns)

  }

}
