package sss.asado.actor

import akka.actor.Actor
import sss.asado.AsadoEvent

import scala.reflect._

/**
  * Created by alan on 8/24/16.
  */
trait AsadoEventSubscribedActor {

  this: Actor =>

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classTag[AsadoEvent].runtimeClass)
  }
}

trait AsadoEventPublishingActor {

  this: Actor =>

  def publish(ev: AsadoEvent) = context.system.eventStream.publish(ev)
}
