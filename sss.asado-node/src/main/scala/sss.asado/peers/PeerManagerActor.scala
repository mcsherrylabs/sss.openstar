package sss.asado.peers

import akka.actor.Actor
import sss.asado.network.{Connection, ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.{ChainQuery, IdQuery, Query}


class PeerManagerActor(messageEventBus: MessageEventBus) extends Actor {

  var queries: Set[Query] = Set()

  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[Connection])

  override def receive: Receive = {

    case q: Query => queries += q

    case connection: Connection =>


  }
}
