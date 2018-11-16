package sss.ui.nobu

import akka.actor.{ActorRef, Props}
import com.vaadin.navigator.{View, ViewChangeListener}
import com.vaadin.ui._
import sss.asado.Status
import sss.asado.block.BlockChainLedger.NewBlockId
import sss.asado.block.{BlockClosedEvent, Synchronized}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.PeerConnection
import sss.ui.nobu.StateActor.StateQueryStatus


import collection.JavaConverters._

object WaitSyncedView {
  val name = "waitSyncedView"
}

class WaitSyncedView(implicit
                     messageEventBus: MessageEventBus,
                     chainId: GlobalChainIdMask,
                     getCurrentHeight: () => Long
                    ) extends VerticalLayout with View with Helpers {

  syncedView =>

  /*def makeCaption(h: Long = getCurrentHeight()) = s"Wait please, synchronizing chain (${h})"

  val btn = new Button(makeCaption())
  btn.addClickListener(uiReactor)

  private val ref = uiReactor.actorOf(Props(WaitSyncActor), btn)

  messageEventBus.subscribe(classOf[BlockClosedEvent])(ref)
  messageEventBus.subscribe(classOf[NewBlockId])(ref)
  messageEventBus.subscribe(classOf[Synchronized])(ref)
  messageEventBus.subscribe(classOf[Status])(ref)
  messageEventBus.subscribe(classOf[PeerConnection])(ref)
  messageEventBus.subscribe(classOf[ConnectionLost])(ref)

  messageEventBus.publish(StateQueryStatus)

  val bar = new ProgressBar()
  bar.setIndeterminate(true)

  setSizeFull()
  setDefaultComponentAlignment(Alignment.MIDDLE_CENTER)
  addComponents(bar, btn)*/

  override def enter(event: ViewChangeListener.ViewChangeEvent): Unit = {
    messageEventBus.publish(StateQueryStatus)
  }


  /*object WaitSyncActor extends UIEventActor {

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

      /*case ComponentEvent(`btn`, _) =>
        val newCap = makeCaption()
        push (btn.setCaption(newCap))*/

      case BlockClosedEvent(`chainId`, height) =>
        val newCap = makeCaption(height)
        push (btn.setCaption(newCap))

      case ConnectionLost(nodeId) =>
        val iter = syncedView.iterator().asScala
        val toRemove = iter.filter(_.getId == nodeId)
        push (toRemove foreach (syncedView.removeComponent(_)))

      case PeerConnection(nodeId, chainId ) =>
        val b = new Button(nodeId)
        b.setId(nodeId)
        b.setEnabled(false)
        push(syncedView.addComponent(b))

      case Status(Synchronized(`chainId`, _, _, _)) | Synchronized(`chainId`, _, _, _) =>
       push (ui.getNavigator.navigateTo(UnlockClaimView.name))
    }
  }*/
}
