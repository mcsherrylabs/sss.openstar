package sss.analysis

import java.util.Date

import akka.actor.Actor
import sss.analysis.BlockSeriesFactory.BlockSeries
import sss.analysis.DashBoard.{Connected, LostConnection, NewBlockAnalysed, status}
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.block.Block
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{NotOrderedEvent, RemoteLeaderEvent, StateMachineInitialised}
import sss.ui.reactor.UIReactor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by alan on 11/3/16.
  */
class AnalysingActor (clientNode: ClientNode) extends Actor with AsadoEventSubscribedActor {

  private case object ConnectHome
  private case class ConnectHomeDelay(delaySeconds: Int = 5)
  private case class Analyse(blockHeight: Long, lastAnalysis: Analysis)
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
      startNetwork
      self ! ConnectHomeDelay()
      context.system.scheduler.scheduleOnce(
        FiniteDuration(config.getInt("analysis.delay"), MINUTES),
        self, CheckForAnalysis(bc.lastBlockHeader.height))


    case CheckForAnalysis(blockHeight) if(!Analysis.isCheckpoint(blockHeight)) =>
      self ! CheckForAnalysis(blockHeight - 1)

    case CheckForAnalysis(blockHeight) =>
      val lastCheckPoint = Analysis(blockHeight, None)
      assert(Analysis.isCheckpoint(blockHeight), s"This height ${blockHeight} must be a checkpoint")
      status.send(s => s.copy(lastAnalysis = lastCheckPoint))
      self ! Analyse(blockHeight + 1, lastCheckPoint)


    case a @ Analyse(blockHeight, prev) if(bc.lastBlockHeader.height < blockHeight) =>
        status.alter(s => s.copy(chainHeight = bc.lastBlockHeader.height, numIds = identityService.list().size))
        context.system.scheduler.scheduleOnce(
          FiniteDuration(1, MINUTES),
          self, a)

    case a @ Analyse(blockHeight, prev) =>
      val block = new Block(blockHeight)
      val chainHeight = bc.lastBlockHeader.height
      log.info("In analysis thread")
      val analysis = Analysis.analyse(block, prev, chainHeight)
      analysis.balance
      self ! Analyse(blockHeight + 1, analysis)
      status.send(s => s.copy(lastAnalysis = analysis, chainHeight = chainHeight, numIds = identityService.list().size))
      UIReactor.eventBroadcastActorRef ! NewBlockAnalysed(analysis, chainHeight)
      log.info("Finish analysis thread")

  }

}
