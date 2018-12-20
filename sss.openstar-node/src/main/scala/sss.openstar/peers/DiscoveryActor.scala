package sss.openstar.peers

import akka.actor.Actor
import sss.openstar.network.MessageEventBus
import sss.openstar.{Send, UniqueNodeIdentifier}

object Discovery {

}

class DiscoveryActor(seedNodes: Set[UniqueNodeIdentifier])
                    (implicit messageEventBus: MessageEventBus,
                     send: Send) extends Actor {

  messageEventBus subscribe()
  send()
  override def receive: Receive = ???
}
