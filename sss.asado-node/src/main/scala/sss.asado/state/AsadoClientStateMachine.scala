package sss.asado.state

import akka.actor.{Actor, ActorLogging, ActorRef, FSM}
import sss.asado.network.Connection
import sss.asado.network.NetworkController._


/**
  * Created by alan on 4/1/16.
  */

trait AsadoClientStateMachine
  extends Actor with FSM[AsadoState.State, Option[Connection]] with ActorLogging {
  import AsadoState._
  import AsadoStateProtocol._

  protected val eventListener: ActorRef

  startWith(ConnectingState, None)

  when(ConnectingState) {
    case Event(cg @ ConnectionGained(conn,_), _) =>
      eventListener ! cg
      goto(OrderedState) using Some(conn)
  }

  onTransition {
    case _ -> OrderedState =>
      self ! SyncWithConnection(nextStateData.get)
      eventListener ! OrderedState
    case _ -> ConnectingState => eventListener ! ConnectingState
    case _ -> ReadyState =>
      eventListener ! ReadyState
  }

  when(OrderedState) {
    case Event(cl @ ConnectionLost(_,_), Some(leaderId)) =>
      eventListener ! cl
      goto(ConnectingState) using None
    case Event(ClientSynced,_) => goto(ReadyState)
  }
  when(ReadyState) {
    case Event(cl @ ConnectionLost(_,_), Some(leaderId)) =>
      eventListener ! cl
      goto(ConnectingState) using None
  }

  whenUnhandled {

    case x =>
      eventListener ! x
      stay()
  }

  initialize()
}

