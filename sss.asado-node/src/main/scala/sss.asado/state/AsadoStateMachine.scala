package sss.asado.state

import akka.actor.{Actor, ActorLogging, FSM}
import block.{IsSynced, NotSynced}
import sss.asado.AsadoEvent
import sss.asado.actor.AsadoEventPublishingActor
import sss.asado.chains.QuorumMonitor.Quorum
import sss.asado.network.Connection
import sss.asado.state.LeaderActor.LeaderFound

/**
  * Created by alan on 4/1/16.
  */
object AsadoStateProtocol {

  case object NotReadyEvent extends AsadoEvent
  case object ReadyStateEvent extends AsadoEvent
  case object QuorumStateEvent extends AsadoEvent
  case object LocalLeaderEvent extends AsadoEvent
  case object NotOrderedEvent extends AsadoEvent
  case object StateMachineInitialised extends AsadoEvent
  case class RemoteLeaderEvent(conn: Connection) extends AsadoEvent

  private[state] case object BlockChainUp
  private[state] case object BlockChainDown

  private[state] case class SplitRemoteLocalLeader(leader: String)
  private[state] case class AcceptTransactions(leader: String)
  private[state] case object StopAcceptingTransactions

}

object AsadoState {
  sealed trait State
  private[state] case object ConnectingState extends State
  private[state] case object QuorumState extends State
  private[state] case object OrderedState extends State
  private[state] case object ReadyState extends State
}

trait AsadoStateMachine
    extends Actor
    with FSM[AsadoState.State, Option[String]]
    with ActorLogging
    with AsadoEventPublishingActor {
  import AsadoState._
  import AsadoStateProtocol._

  startWith(ConnectingState, None)

  when(ConnectingState) {
    case Event(Quorum(_), _) => goto(QuorumState)
  }

  onTransition {
    case _ -> QuorumState => publish(QuorumStateEvent)
    case QuorumState -> OrderedState =>
      self ! SplitRemoteLocalLeader(nextStateData.get)
    case OrderedState -> QuorumState     => publish(NotOrderedEvent)
    case OrderedState -> ConnectingState => publish(NotOrderedEvent)
    case OrderedState -> ReadyState =>
      publish(ReadyStateEvent)
      self ! AcceptTransactions(nextStateData.get)
    case ReadyState -> _ =>
      publish(NotReadyEvent)
      self ! StopAcceptingTransactions

    /*case ReadyState -> OrderedState =>
      self ! StopAcceptingTransactions
    case ReadyState -> ConnectingState =>
      self ! StopAcceptingTransactions
      self ! Connecting*/
    //case _ -> ConnectingState => self ! Connecting
  }

  when(QuorumState) {
    case Event(LeaderFound(leaderId), _) =>
      goto(OrderedState) using Some(leaderId)
  }

  when(OrderedState) {
    case Event(IsSynced, _) => goto(ReadyState)
  }

  when(ReadyState) {
    case Event(NotSynced, _) => goto(OrderedState)
  }

  whenUnhandled {
    /*case Event(QuorumLost, _)           => goto(ConnectingState) using None
    case Event(ConnectionLost(_, _), _) => stay()
    case Event(PeerConnectionLost(conn, _), Some(leaderId))
        if (conn.nodeId.id == leaderId) =>
      goto(QuorumState) using None*/
    case x => log.warning(x.toString); stay()
  }

  initialize()
}
