package sss.asado.state

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging}

/**
  * Created by alan on 7/28/16.
  */
class EventListener extends Actor with ActorLogging {

  override def receive: Receive = {
    case x => log.info(s"Default event listener receive $x")
  }

}
