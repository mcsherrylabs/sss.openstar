package sss.asado.network


import java.util.concurrent.atomic.AtomicReference

import akka.actor.Actor

class ConnectionTracker(
                         atomicReference: AtomicReference[Set[Connection]],
                         eventBus: MessageEventBus
                       ) extends Actor {

  override def receive: Receive = ??? //TODO delete

  /*var connections: Set[Connection] = Set()

  eventBus.subscribe(classOf[ConnectionLost])
  eventBus.subscribe(classOf[Connection])

  override def receive: Receive = {
    case c: Connection =>
      connections += c
      atomicReference.set(connections)

    case l @ ConnectionLost(n) =>
      connections = connections.filterNot(_.nodeId == n)
      atomicReference.set(connections)
  }*/
}
