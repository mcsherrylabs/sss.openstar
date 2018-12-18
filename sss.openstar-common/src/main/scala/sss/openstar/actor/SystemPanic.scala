package sss.openstar.actor

import akka.actor.{Actor, ActorLogging}

import scala.reflect.classTag

case object AllStop

trait SystemPanic  {

  this: Actor with ActorLogging =>

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classTag[AllStop.type].runtimeClass)
  }

  def systemPanic(e: Throwable): Unit = {
    log.error("System panic {} ", e)
    systemPanic()
  }

  def systemPanic(): Unit = {
    log.error(s"***** Game over man, game over *****")
    context.system.eventStream.publish(AllStop)
    context.system.terminate()
  }

  def stopOnAllStop: Receive = {
    case AllStop => context stop self
  }

}
