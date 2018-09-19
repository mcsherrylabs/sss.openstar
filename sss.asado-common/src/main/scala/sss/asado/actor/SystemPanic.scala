package sss.asado.actor

import akka.actor.{Actor, ActorLogging}

import scala.reflect.classTag

case object AllStop

trait SystemPanic  {

  this: Actor with ActorLogging =>

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classTag[AllStop.type].runtimeClass)
  }

  def systemPanic(): Unit = {
    log.error(s"***** Game over man, game over *****")
    context.system.eventStream.publish(AllStop)
  }

  def stopOnAllStop: Receive = {
    case AllStop => context stop self
  }

}
