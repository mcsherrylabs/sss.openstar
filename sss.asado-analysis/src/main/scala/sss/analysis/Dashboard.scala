package sss.analysis

import akka.actor.{ActorRef, Props}
import akka.agent.Agent
import com.vaadin.ui._
import sss.analysis.Analysis.AnalysisFromMemory
import sss.analysis.DashBoard.{Connected, LostConnection, NewBlockAnalysed}
import sss.ancillary.Logging
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{NotReadyEvent, RemoteLeaderEvent}
import sss.ui.reactor.{ComponentEvent, Event, Register, UIEventActor, UIReactor}

import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by alan on 10/26/16.
  */

object DashBoard {
  trait DashBoardEvent extends Event {
    override val category: String = "dashBoard"
  }
  case class NewBlockAnalysed(blockAnalysis: Analysis) extends DashBoardEvent
  case class Connected(node: String) extends DashBoardEvent
  case object LostConnection extends DashBoardEvent

  case class Status(lastAnalysis: Analysis, whoConnectedTo: String)
  lazy val status: Agent[Status] = Agent(Status(Analysis.blockOneAnalysis, "Not Connected"))
}
class Dashboard(uiReactor: UIReactor, clientNode: ClientNode) extends TabSheet with Logging {

  import DashBoard.status

  val summary = new Summary(uiReactor)
  val blocksTab = new BlocksTab(clientNode)
  val idsTab = new IdentitiesTab(clientNode, status)
  val txsTab = new TransactionsTab(clientNode)

  import summary._

  val ref = uiReactor.actorOf(Props(UICoordinatingActor),
    numBlocksLbl, identitiesLbl, txsLbl)

  ref ! Register("dashBoard")

  addTab(summary, "Summary")
  addTab(blocksTab, "Analysed Blocks")
  addTab(idsTab, "Identities")

  val latestStatus = status.get

  blocksTab.update(latestStatus.lastAnalysis.analysisHeight)
  summary.setConnected(latestStatus.whoConnectedTo)
  updateDash(latestStatus.lastAnalysis)

  private def updateDash(blockAnalysis: Analysis): Unit = {
    summary.setBalance(blockAnalysis.balance)
    summary.setBlockCount(blockAnalysis.analysisHeight)
    summary.setIdentitiesCount(idsTab.idCount.get())
    summary.setTxCount(blockAnalysis.txTotal)
  }

  val dashboardThis: Dashboard = this

  object UICoordinatingActor extends UIEventActor  {

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {
      case ComponentEvent(`numBlocksLbl`,_) => push {
        dashboardThis.setSelectedTab(blocksTab)
        blocksTab.update(numBlocksLbl.getCaption.toLong)
      }

      case ComponentEvent(`identitiesLbl`,_) => push {
        dashboardThis.setSelectedTab(idsTab)
        idsTab.update()
      }

      case ComponentEvent(`txsLbl`,_) => push {
        dashboardThis.setSelectedTab(txsTab)
      }
      case Connected(who) => push { setConnected(who)}
      case LostConnection => push { setConnected("Disconnected")}
      case NewBlockAnalysed(bh) => push { updateDash(bh)  }
    }
  }
}
