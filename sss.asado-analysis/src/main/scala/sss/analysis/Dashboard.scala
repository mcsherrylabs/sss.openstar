package sss.analysis

import akka.actor.{ActorRef, Props}
import com.vaadin.ui._
import sss.ancillary.Logging
import sss.asado.nodebuilder.ClientNode
import sss.ui.reactor.{ComponentEvent, UIEventActor, UIReactor}

/**
  * Created by alan on 10/26/16.
  */

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

  val dashboardThis: Dashboard = this

  object UICoordinatingActor extends UIEventActor {
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
    }
  }
}
