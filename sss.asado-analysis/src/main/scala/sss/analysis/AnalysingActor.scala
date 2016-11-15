package sss.analysis

import java.util.Date

import akka.actor.Actor
import sss.analysis.DashBoard.{Connected, LostConnection, NewBlockAnalysed, status}
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
  private case class ConnectHomeDelay(delaySeconds: Int = 5)
  private case class Analyse(block: Long)
  private case class CheckForAnalysis(block: Long)

  import clientNode._

  override def receive: Receive = connecting orElse analysis

  private def connecting: Receive = {
    case RemoteLeaderEvent(conn) =>
      context.become(connected(conn.nodeId.id) orElse analysis)
      status.alter(s => s.copy(whoConnectedTo = conn.nodeId.id))
      UIReactor.eventBroadcastActorRef ! Connected(conn.nodeId.id)


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
      status.alter(s => s.copy(whoConnectedTo = "Disconnected"))
      UIReactor.eventBroadcastActorRef ! LostConnection
      context.become(connecting orElse analysis)
      self ! ConnectHomeDelay()

    case ConnectHome => log.info("Already connected, ignore ConnectHome")
  }

  private def analysis: Receive = {
    case StateMachineInitialised =>
      //startNetwork
      //self ! ConnectHomeDelay()
      self ! CheckForAnalysis(2)

    case CheckForAnalysis(blockHeight) if(Analysis.isAnalysed(blockHeight)) =>
      self ! CheckForAnalysis(blockHeight + 1)

    case CheckForAnalysis(blockHeight) =>
      self ! Analyse(blockHeight)
      status.alter(s => s.copy(lastAnalysis = Analysis(blockHeight - 1)))

    case a @ Analyse(blockHeight) if(bc.lastBlockHeader.height < blockHeight) =>
        context.system.scheduler.scheduleOnce(
          FiniteDuration(5, MINUTES),
          self, a)

    case a @ Analyse(blockHeight) =>
      val block = new Block(blockHeight)
      val start = new Date().getTime
      val analysis = Analysis.analyse(block)
      val took = new Date().getTime - start
      log.info(s"Block $blockHeight took $took")
      self ! Analyse(blockHeight + 1)
      status.alter(s => s.copy(lastAnalysis = analysis))
      UIReactor.eventBroadcastActorRef ! NewBlockAnalysed(analysis)

  }

}
