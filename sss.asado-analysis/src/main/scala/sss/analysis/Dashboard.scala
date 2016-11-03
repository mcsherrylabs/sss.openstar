package sss.analysis

import akka.actor.{ActorRef, Props}
import com.vaadin.ui._
import sss.analysis.DashBoard.{Connected, LostConnection}
import sss.ancillary.Logging
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{NotReadyEvent, RemoteLeaderEvent}
import sss.ui.reactor.{ComponentEvent, Event, Register, UIEventActor, UIReactor}

/**
  * Created by alan on 10/26/16.
  */

object DashBoard {
  trait DashBoardEvent extends Event {
    override val category: String = "dashBoard"
  }
  case class Connected(node: String) extends DashBoardEvent
  case object LostConnection extends DashBoardEvent

}
class Dashboard(uiReactor: UIReactor, clientNode: ClientNode) extends TabSheet with Logging {


  val summary = new Summary(uiReactor)
  val blocksTab = new BlocksTab(clientNode)
  val idsTab = new IdentitiesTab(clientNode)
  val txsTab = new TransactionsTab(clientNode)

  import summary._

  uiReactor.actorOf(Props(UICoordinatingActor),
    numBlocksLbl, identitiesLbl, txsLbl)

  addTab(summary, "Summary")
  addTab(blocksTab, "Blocks")
  addTab(idsTab, "Identities")

  summary.setBlockCount(clientNode.bc.lastBlockHeader.height)
  summary.setIdentitiesCount(idsTab.idCount.get())

  val dashboardThis: Dashboard = this

  object UICoordinatingActor extends UIEventActor  {

    self ! Register("dashBoard")

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {
      case ComponentEvent(`numBlocksLbl`,_) => push {
        dashboardThis.setSelectedTab(blocksTab)
        blocksTab.update(numBlocksLbl.getCaption.toLong)
        log.info("It's done!")
      }

      case ComponentEvent(`identitiesLbl`,_) => push {
        dashboardThis.setSelectedTab(idsTab)
        idsTab.update()
        log.info("It's done ids!")
      }
      case ComponentEvent(`txsLbl`,_) => push {
        dashboardThis.setSelectedTab(txsTab)
      }
      case Connected(who) => push { setConnected(who)}
      case LostConnection => push { setConnected("Disconnected")}
    }
  }
}
