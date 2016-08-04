package sss.asado.state

import akka.actor.{Actor, ActorLogging, FSM}
import sss.asado.network.Connection
import sss.asado.network.NetworkController.{ConnectionLost, PeerConnectionLost, QuorumGained, QuorumLost}


/**
  * Created by alan on 4/1/16.
  */
object AsadoStateProtocol {

  sealed trait ProtocolEvent
  case object RegisterStateEvents
  case object DeRegisterStateEvents
  case class Propagate(state: ProtocolEvent)
  case object FindTheLeader
  case class LeaderFound(leader: String)
  case object NotReadyEvent extends ProtocolEvent
  case object ReadyStateEvent extends ProtocolEvent
  case object QuorumStateEvent extends ProtocolEvent
  case object LocalLeaderEvent extends ProtocolEvent
  case object NotOrderedEvent extends ProtocolEvent

  // Fired when the client has downloaded up to the latest
  case object ClientSynced
  // Fired when the network leader has got a quorum of synced nodes
  case object Synced
  case object NotSynced

  case class RemoteLeaderEvent(conn: Connection) extends ProtocolEvent
  case class SplitRemoteLocalLeader(leader: String)
  case class AcceptTransactions(leader: String)
  case object StopAcceptingTransactions
  case object BlockChainUp
  case object BlockChainDown
  case object StateMachineInitialised
}

object AsadoState {
  sealed trait State
  private[state] case object ConnectingState extends State
  private[state] case object QuorumState extends State
  private[state] case object OrderedState extends State
  private[state] case object ReadyState extends State
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
    case _ -> QuorumState => self ! Propagate(QuorumStateEvent)
    case QuorumState -> OrderedState => self ! SplitRemoteLocalLeader(nextStateData.get)
    case OrderedState -> QuorumState => self ! Propagate(NotOrderedEvent)
    case OrderedState -> ConnectingState => self ! Propagate(NotOrderedEvent)
    case OrderedState -> ReadyState =>
      self ! Propagate(ReadyStateEvent)
      self ! AcceptTransactions(nextStateData.get)
    case ReadyState -> _ =>
      self ! Propagate(NotReadyEvent)
      self ! StopAcceptingTransactions

    /*case ReadyState -> OrderedState =>
      self ! StopAcceptingTransactions
    case ReadyState -> ConnectingState =>
      self ! StopAcceptingTransactions
      self ! Connecting*/
    //case _ -> ConnectingState => self ! Connecting
  }

  when(QuorumState) {
    case Event(LeaderFound(leaderId),_) => goto(OrderedState) using Some(leaderId)
  }

  when(OrderedState) {
    case Event(Synced,_) => goto(ReadyState)
  }

  when(ReadyState) {
    case Event(NotSynced, _) => goto(OrderedState)
  }

  whenUnhandled {
    case Event(QuorumLost, _) => goto(ConnectingState) using None
    case Event(ConnectionLost(_,_), _) => stay()
    case Event(PeerConnectionLost(conn, _), Some(leaderId)) if(conn.nodeId.id == leaderId) => goto(QuorumState) using None
    case x => log.warning(x.toString); stay()
  }

  initialize()
}

