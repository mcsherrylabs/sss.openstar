package sss.asado.state

import akka.actor.{Actor, ActorLogging, FSM}
import sss.asado.block.ClientSynced
import sss.asado.AsadoEvent
import sss.asado.actor.AsadoEventPublishingActor
import sss.asado.network.{Connection, ConnectionLost}


/**
  * Created by alan on 4/1/16.
  */
trait AsadoClientStateMachine
    extends Actor
    with FSM[AsadoState.State, Option[Connection]]
    with ActorLogging
    with AsadoEventPublishingActor {
  import AsadoState._
  import AsadoStateProtocol._

  startWith(ConnectingState, None)

  when(ConnectingState) {
    case Event(cg @ Connection( _), _) =>
      goto(OrderedState) using None // was Some(conn)
  }

  onTransition {
    case _ -> OrderedState =>
      self ! RemoteLeaderEvent(nextStateData.get)
    case _ -> ConnectingState => publish(NotOrderedEvent)
    case _ -> ReadyState      => publish(ReadyStateEvent)
  }

  when(OrderedState) {
    case Event(cl @ ConnectionLost(_), Some(leaderId)) =>
      goto(ConnectingState) using None
    case Event(ClientSynced, _) => goto(ReadyState)
  }
  when(ReadyState) {
    case Event(cl @ ConnectionLost(_), Some(leaderId)) =>
      goto(ConnectingState) using None
  }

  whenUnhandled {

    case Event(x: AsadoEvent, _) =>
      publish(x)
      stay()
  }

  initialize()
}
