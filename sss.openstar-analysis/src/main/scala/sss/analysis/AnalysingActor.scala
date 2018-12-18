package sss.analysis


import akka.actor.Actor
import org.joda.time.LocalDateTime
import sss.analysis.Main.ClientNode
import sss.openstar.UniqueNodeIdentifier
import sss.ui.DashBoard.{Connected, LostConnection, NewBlockAnalysed, status}
import sss.openstar.actor.OpenstarEventSubscribedActor
import sss.openstar.network.ConnectionLost
import sss.openstar.peers.PeerManager.PeerConnection
import sss.ui.reactor.UIReactor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by alan on 11/3/16.
  */
class AnalysingActor (clientNode: ClientNode) extends Actor with OpenstarEventSubscribedActor {

  private case object ConnectHome
  private case class ConnectHomeDelay(delaySeconds: Int = 5)
  private case class Analyse(blockHeight: Long, lastAnalysis: Analysis)
  private case class CheckForAnalysis(block: Long)

  import clientNode._

  messageEventBus subscribe classOf[PeerConnection]
  messageEventBus subscribe classOf[ConnectionLost]

  private var connectedTo: Option[UniqueNodeIdentifier] = None

  override def receive: Receive = analysis


  private def analysis: Receive = {

    case PeerConnection(nId, _) =>
      connectedTo = Some(nId)
      status.alter(s => s.copy(whoConnectedTo = nId))
      UIReactor.eventBroadcastActorRef ! Connected(nId)
      self ! CheckForAnalysis(bc.lastBlockHeader.height)

    case ConnectionLost(nId) if(Option(nId) == connectedTo) =>
      status.alter(s => s.copy(whoConnectedTo = "Disconnected"))
      connectedTo = None
      UIReactor.eventBroadcastActorRef ! LostConnection

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
      val block = bc.block(blockHeight)
      val blockTime = bc.blockHeader(blockHeight).time
      val chainHeight = bc.lastBlockHeader.height
      log.info("In analysis thread")
      val analysis = Analysis.analyse(block, prev, chainHeight, new LocalDateTime(blockTime.getTime))
      analysis.balance
      self ! Analyse(blockHeight + 1, analysis)
      status.send(s => s.copy(lastAnalysis = analysis, chainHeight = chainHeight, numIds = identityService.list().size))
      UIReactor.eventBroadcastActorRef ! NewBlockAnalysed(analysis, chainHeight)
      log.info("Finish analysis thread")

  }

}
