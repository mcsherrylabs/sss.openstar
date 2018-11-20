package sss.ui.nobu

import akka.actor.{Actor, ActorSystem, Props}
import com.vaadin.navigator.{View, ViewChangeListener}
import com.vaadin.ui._
import sss.asado.Status
import sss.asado.block.{BlockClosedEvent, Synchronized}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.PeerConnection
import sss.asado.network.MessageEventBus._
import sss.ui.nobu.NobuUI.{Detach, SessionEnd}
import sss.ui.nobu.StateActor.StateQueryStatus

import collection.JavaConverters._

object WaitSyncedView {
  val name = "waitSyncedView"
}

class WaitSyncedView(implicit
                     val ui: UI,
                     messageEventBus: MessageEventBus,
                     chainId: GlobalChainIdMask,
                     actorSystem: ActorSystem,
                     getCurrentHeight: () => Long
                    ) extends VerticalLayout with View with LayoutHelper {

  syncedView =>

  def makeCaption(h: Long = getCurrentHeight()) = s"Wait please, synchronizing chain (${h})"

  val btn = new Button(makeCaption())

  btn addClickListener( e =>
      e.getButton.setCaption(makeCaption())
    )

  private val ref = actorSystem.actorOf(Props(WaitSyncActor), s"WaitSync_${sessId}")

  messageEventBus.subscribe(classOf[Detach])(ref)

  val interestingEvents = Seq(
    classOf[BlockClosedEvent],
    classOf[Synchronized],
    classOf[Status],
    classOf[PeerConnection],
    classOf[ConnectionLost]
  )

  val bar = new ProgressBar()
  bar.setIndeterminate(true)

  setSizeFull()
  setDefaultComponentAlignment(Alignment.MIDDLE_CENTER)
  addComponents(bar, btn)

  override def enter(event: ViewChangeListener.ViewChangeEvent): Unit = {

    interestingEvents.subscribe(ref)
    messageEventBus.publish(StateQueryStatus)
  }


  object WaitSyncActor extends Actor {

    override def receive: Receive = {

      case Detach(uiId) if(getUI.getUIId == uiId) =>
        context stop self

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
        interestingEvents.unsubscribe(self)
        push (navigator.navigateTo(UnlockClaimView.name))
    }
  }
}
