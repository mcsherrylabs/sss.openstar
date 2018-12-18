package sss.openstar.actor

import akka.actor.Actor
import sss.openstar.OpenstarEvent

import scala.reflect._

/**
  * Created by alan on 8/24/16.
  */
trait OpenstarEventSubscribedActor {

  this: Actor =>

  override def preStart(): Unit = {
    context.system.eventStream
      .subscribe(self, classTag[OpenstarEvent].runtimeClass)
  }
}

trait OpenstarEventPublishingActor {

  this: Actor =>

  def publish(ev: OpenstarEvent) = context.system.eventStream.publish(ev)
}
