package sss.ui.nobu

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.actor.Actor.Receive
import akka.routing.RoundRobinPool
import com.vaadin.ui.{Notification, UI}
import sss.ancillary.Logging
import sss.asado.AsadoEvent
import sss.asado.network.MessageEventBus
import sss.ui.nobu.BlockingWorkers.BlockingTask



trait BlockingWorkerUIHelper {

  self: Logging =>

  protected def sessId(implicit ui: UI): String = ui.getSession.getSession.getId

  def push(f: => Unit)(implicit ui: UI): Unit = PushHelper.push(f)

  protected def show(msg: String, t: Notification.Type)(implicit ui: UI): Unit = {
    push(
      Notification.show(
        msg,
        t)
    )
  }

  protected def navigateTo(viewName:String)(implicit ui: UI): Unit = {
    push (
      ui.getNavigator.navigateTo(UnlockClaimView.name)
    )
  }
}

object BlockingWorkers {

  case class BlockingTask(any: Any) extends AsadoEvent

  def apply(r: Receive)(implicit actorSystem: ActorSystem, messageEventBus: MessageEventBus): ActorRef = {

      val router = actorSystem.actorOf(RoundRobinPool(16)
        .props(Props(classOf[BlockingWorker], r))
        .withDispatcher("blocking-dispatcher"), "router")
      messageEventBus.subscribe(classOf[BlockingTask])(router)
    router
  }
}

class BlockingWorker(r: Receive) extends Actor with ActorLogging {

  override def receive: Receive = r orElse {
    case BlockingTask(a) => self ! a
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled in Blocking Actor Router {}", message)
  }
}

