package sss.asado.state

import akka.actor.{Actor, ActorLogging, FSM}
import sss.asado.block.{IsSynced, NotSynced}
import sss.asado.AsadoEvent
import sss.asado.actor.AsadoEventPublishingActor
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.network.{Connection, ConnectionLost}
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
    case Event(Quorum(_, _, _), _) => goto(QuorumState)
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
    case Event(QuorumLost(_), _)           => goto(ConnectingState) using None
    //case Event(ConnectionLost(_, _), _) => stay()
    case Event(ConnectionLost(nodeId), Some(leaderId))
        if (nodeId == leaderId) =>
      goto(QuorumState) using None
    case x => log.warning(x.toString); stay()
  }

  initialize()
}
