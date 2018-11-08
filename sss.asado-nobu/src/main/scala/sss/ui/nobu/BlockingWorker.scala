package sss.ui.nobu

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.actor.Actor.Receive
import akka.routing.RoundRobinPool

object BlockingWorkers {

  def apply(r: Receive)(implicit actorSystem: ActorSystem): BlockingWorkers = {

      new BlockingWorkers(
        actorSystem.actorOf(RoundRobinPool(16)
        .props(Props(classOf[BlockingWorker], r))
        .withDispatcher("blocking-dispatcher"), "router")
      )
  }
}

class BlockingWorker(r: Receive) extends Actor with ActorLogging {

  override def receive: Receive = r

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled in Blocking Actor Router {}", message)
  }
}

class BlockingWorkers(router: ActorRef) {

  def submit(a: Any)(implicit sender: ActorRef) = router.tell(a, sender)

}
