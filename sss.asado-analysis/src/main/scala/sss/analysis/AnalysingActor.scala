package sss.analysis

import akka.actor.Actor
import sss.analysis.DashBoard.{Connected, LostConnection}
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.block.Block
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{NotOrderedEvent, RemoteLeaderEvent, StateMachineInitialised}
import sss.ui.reactor.UIReactor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by alan on 11/3/16.
  */
class AnalysingActor (clientNode: ClientNode) extends Actor with AsadoEventSubscribedActor {

  private case object ConnectHome
  private case object BroadcastConnected
  private case class ConnectHomeDelay(delaySeconds: Int = 5)
  private case class Analyse(block: Long)

  import clientNode._

  override def receive: Receive = connecting orElse analysis

  private def connecting: Receive = {
    case RemoteLeaderEvent(conn) =>
      context.become(connected(conn.nodeId.id) orElse analysis)
      self ! BroadcastConnected

    case ConnectHomeDelay(delay) =>
      context.system.scheduler.scheduleOnce(
        FiniteDuration(delay, SECONDS),
        self, ConnectHome)

    case ConnectHome =>
      connectHome
      self ! ConnectHomeDelay()

  }

  private def connected(connectedTo: String): Receive = {
    case NotOrderedEvent =>
      UIReactor.eventBroadcastActorRef ! LostConnection
      context.become(connecting orElse analysis)
      self ! ConnectHomeDelay()

    case BroadcastConnected =>
      UIReactor.eventBroadcastActorRef ! Connected(connectedTo)
      context.system.scheduler.scheduleOnce(
        FiniteDuration(5, SECONDS),
        self, BroadcastConnected)


    case ConnectHome => log.info("Already connected, ignore ConnectHome")
  }

  private def analysis: Receive = {
    case StateMachineInitialised =>
      //startNetwork
      //self ! ConnectHomeDelay()

    case a @ Analyse(blockHeight) =>
      Analysis.opt(blockHeight) match {
        case None if(bc.lastBlockHeader.height < blockHeight) =>
          context.system.scheduler.scheduleOnce(
            FiniteDuration(5, MINUTES),
            self, a)
        case None =>
            val block = bc.block(blockHeight)
            Analysis.opt(blockHeight - 1) match {
              case None => log.warn(s"No analysis for previous block, cannot analyse this height? $blockHeight")
              case Some(a) => Analysis.analyse(block, a)
            }

        case Some(analysis) => self ! Analyse(blockHeight + 1)
      }
  }

}
