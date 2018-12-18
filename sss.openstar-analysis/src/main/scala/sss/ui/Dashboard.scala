package sss.ui

import akka.actor.{ActorRef, Props}
import akka.agent.Agent
import com.vaadin.ui._
import sss.analysis.Main.ClientNode
import sss.analysis._
import sss.ancillary.Logging
import sss.ui.DashBoard._
import sss.ui.reactor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 10/26/16.
  */

object DashBoard {
  trait DashBoardEvent extends Event {
    override val category: String = "dashBoard"
  }
  case class NewBlockAnalysed(blockAnalysis: Analysis, chainHeight: Long) extends DashBoardEvent
  case class Connected(node: String) extends DashBoardEvent
  case object LostConnection extends DashBoardEvent

  case class Status(lastAnalysis: Analysis, whoConnectedTo: String, chainHeight: Long, numIds: Long)
  lazy val status: Agent[Status] = Agent(Status(Analysis.blockOneAnalysis, "Not Connected", 0,0 ))
}
class DashBoard(uiReactor: UIReactor, clientNode: ClientNode) extends TabSheet with Logging {

  import DashBoard.status

  val summary = new Summary(uiReactor, status)
  val blocksTab = new BlocksTab(clientNode)
  val idsTab = new IdentitiesTab(clientNode, status)
  val walletsTab = new WalletsTab(clientNode, status)
  val chartsTab = new ChartsTab(clientNode)
  val exportTxsTab = new QueryTxsTab(new TransactionHistoryPersistence()(clientNode.db))

  val tabSheet = this

  import summary._

  val ref = uiReactor.actorOf(Props(UICoordinatingActor).withDispatcher("my-pinned-dispatcher"),
    numBlocksLbl, identitiesLbl, txsLbl, tabSheet)

  ref ! Register("dashBoard")

  setMargin(true)
  setSpacing(true)

  addTab(summary, "Summary")
  addTab(blocksTab, "Analysed Blocks")
  addTab(idsTab, "Identities")
  addTab(walletsTab, "Wallets")
  addTab(chartsTab, "Charts")
  addTab(exportTxsTab, "Export")

  addSelectedTabChangeListener(uiReactor)

  summary.update

  val dashboardThis: DashBoard = this


  object UICoordinatingActor extends UIEventActor  {

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {
      case ComponentEvent(`numBlocksLbl`,_) => push {
        dashboardThis.setSelectedTab(blocksTab)
      }

      case ComponentEvent(`identitiesLbl`,_) => push {
        dashboardThis.setSelectedTab(idsTab)
      }

      case ComponentEvent(`tabSheet`, _) =>
        tabSheet.getSelectedTab match {
          case `walletsTab` => walletsTab.update(push _)
          case `chartsTab` =>
            val startBlock = Try{
              val analysedBlockCount = numBlocksLbl.getCaption.toLong
              if(analysedBlockCount > 1000) analysedBlockCount - 1000
              else 2
            } match {
              case Failure(_) => 2
              case Success(s) => s
            }
            chartsTab.update(startBlock)

          case `blocksTab` => push(Try(numBlocksLbl.getCaption.toLong) map (blocksTab.update(_)))

          case `idsTab` => idsTab.update(push _)
          case _ =>
        }


      case Connected(who) => push { summary.setConnected(who)}
      case LostConnection => push { summary.setConnected("Disconnected")}
      case NewBlockAnalysed(bh, chainHeight) => push { summary.update }
    }
  }
}
