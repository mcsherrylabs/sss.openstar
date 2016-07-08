package sss.asado.state

import akka.actor.{Actor, ActorLogging, FSM}
import sss.asado.network.Connection
import sss.asado.network.NetworkController._


/**
  * Created by alan on 4/1/16.
  */

trait AsadoClientStateMachine
  extends Actor with FSM[AsadoState.State, Option[Connection]] with ActorLogging {
  import AsadoState._
  import AsadoStateProtocol._

  startWith(ConnectingState, None)

  when(ConnectingState) {
    case Event(ConnectionGained(conn,_), _) => goto(OrderedState) using Some(conn)
  }

  onTransition {
    case _ -> OrderedState => self ! SyncWithConnection(nextStateData.get)
    case _ -> ConnectingState => self ! Connecting
  }


  when(OrderedState) {
    case Event(Synced,_) => goto(ReadyState)
  }

  when(ReadyState) {
    case Event(NotSynced, _) => goto(OrderedState)
  }

  whenUnhandled {
    case Event(ConnectionLost(_,_), Some(leaderId)) => goto(ConnectingState) using None
    case x => log.warning(x.toString); stay()
  }

  initialize()
}

