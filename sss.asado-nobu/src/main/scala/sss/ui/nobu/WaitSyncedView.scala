package sss.ui.nobu

import akka.actor.{ActorRef, Props}
import com.vaadin.navigator.{View, ViewChangeListener}
import com.vaadin.ui._
import sss.asado.block.Synchronized
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.PeerConnection
import sss.ui.reactor.{ComponentEvent, UIEventActor, UIReactor}
import collection.JavaConverters._

object WaitSyncedView {
  val name = "waitSyncedView"
}

class WaitSyncedView(implicit uiReactor: UIReactor,
                     messageEventBus: MessageEventBus,
                     chainId: GlobalChainIdMask,
                     getCurrentHeight: () => Long
                    ) extends VerticalLayout with View {

  syncedView =>

  def makeCaption() = s"Wait please, synchronizing chain (${getCurrentHeight()})"

  val btn = new Button(makeCaption())
  btn.addClickListener(uiReactor)

  private val ref = uiReactor.actorOf(Props(WaitSyncActor), btn)

  setSizeFull()
  setDefaultComponentAlignment(Alignment.MIDDLE_CENTER)
  addComponent(btn)

  override def enter(event: ViewChangeListener.ViewChangeEvent): Unit = {

  }

  object WaitSyncActor extends UIEventActor {

    messageEventBus.subscribe(classOf[Synchronized])
    messageEventBus.subscribe(classOf[PeerConnection])
    messageEventBus.subscribe(classOf[ConnectionLost])

    /*import concurrent.duration._
    import context.dispatcher
    import scala.language.postfixOps

    context.system.scheduler.scheduleOnce(10 seconds, self, Synchronized(chainId, 0, 0))*/

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

      case ComponentEvent(`btn`, _) =>
        val newCap = makeCaption()
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


      case Synchronized(`chainId`, _, _) =>
       push (getUI().getNavigator.navigateTo(UnlockClaimView.name))
    }
  }
}
