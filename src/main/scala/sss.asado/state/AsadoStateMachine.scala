package sss.asado.state

import akka.actor.{Actor, ActorLogging, FSM}
import sss.asado.network.NetworkController.{ConnectionLost, PeerConnectionLost}


/**
  * Created by alan on 4/1/16.
  */
object AsadoStateProtocol {
  case object QuorumLost
  case object QuorumGained
  case class LeaderFound(leaderId: String)
  case object Synced
  case object NotSynced

  case object FindTheLeader
  case class SyncWithLeader(leader: String)
  case class AcceptTransactions(leader: String)
  case object StopAcceptingTransactions
  case object Connecting
  case object BlockChainUp
  case object BlockChainDown
}

object AsadoState {
  sealed trait State
  case object ConnectingState extends State
  case object QuorumState extends State
  case object OrderedState extends State
  case object ReadyState extends State
}

trait AsadoStateMachine
  extends Actor with FSM[AsadoState.State, Option[String]] with ActorLogging {
  import AsadoState._
  import AsadoStateProtocol._

  startWith(ConnectingState, None)

  when(ConnectingState) {
    case Event(QuorumGained, _) => goto(QuorumState)
  }

  onTransition {
    case _ -> QuorumState => self ! FindTheLeader
    case QuorumState -> OrderedState => self ! SyncWithLeader(nextStateData.get)
    case OrderedState -> ReadyState => self ! AcceptTransactions(nextStateData.get)
    case ReadyState -> OrderedState =>
      self ! StopAcceptingTransactions
    case ReadyState -> ConnectingState =>
      self ! StopAcceptingTransactions
      self ! Connecting
    case _ -> ConnectingState => self ! Connecting
  }

  when(QuorumState) {
    case Event(LeaderFound(leaderId),_) => goto(OrderedState) using Some(leaderId)
  }

  when(OrderedState) {
    case Event(Synced,_) => goto(ReadyState)
  }

  when(ReadyState) {
    case Event(NotSynced, _) => goto(OrderedState) using None
  }

  whenUnhandled {
    case Event(QuorumLost, _) => goto(ConnectingState) using None
    case Event(ConnectionLost, _) => stay()
    case Event(PeerConnectionLost(conn, _), Some(leaderId)) if(conn.nodeId.id == leaderId) => goto(QuorumState) using None
    case x => log.warning(x.toString); stay()
  }

  initialize()
}

