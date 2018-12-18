package sss.ui.nobu

import java.util.concurrent.atomic.AtomicReference

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import com.vaadin.ui.UI
import sss.openstar.block.{NotSynchronized, Synchronized}
import sss.openstar.message.{FailureResponse, MessageDownloadActor, SuccessResponse}
import sss.openstar.network.MessageEventBus.IncomingMessage
import sss.openstar.wallet.UtxoTracker.NewLodgement
import sss.openstar._
import sss.openstar.message.MessageDownloadActor.CheckForMessages
import sss.openstar.state.HomeDomain
import sss.openstar.wallet.Wallet
import sss.ui.nobu.Main.ClientNode
import sss.ui.nobu.NobuUI.{Detach, SessionEnd}
import sss.ui.nobu.UIActor._

/**
  * Created by alan on 11/9/16.
*/

trait OnSessionEnd {

  actor: Actor =>

  val refs: AtomicRefMap

  def sessionEnd(sessId: String): Receive = {
    case SessionEnd(`sessId`) =>
      refs.getAndUpdate( all => {
        all - sessId
      })
      context stop self

  }
}

object UIActor {

  type AtomicRefMap = AtomicReference[Map[String, ActorRef]]

  private val uiActors: AtomicRefMap = new AtomicReference[Map[String, ActorRef]](Map.empty)

  case class WithUI(ui: UI)
  case class TrackSessionRef(sessId: String,
                             ref: ActorRef) extends OpenstarEvent

  case class UnTrackLodgements(sessId: String) extends OpenstarEvent
  case class TrackLodgements(sessId: String, who: UniqueNodeIdentifier, f: () => Unit) extends OpenstarEvent
  case class UnSubscribe(cls: Class[_], uiId: String) extends OpenstarEvent
  case class TrackMsgTxId(sessId: String, txIdStr: String) extends OpenstarEvent

  def apply(clientNode: ClientNode, ui: UI)(implicit as: ActorSystem): ActorRef = {

    def makeRef(sessId: String): ActorRef = {
      val ref = as.actorOf(Props(classOf[UIActor], clientNode, uiActors), s"UIActor${sessId}")
      ref ! WithUI(ui)
      clientNode.messageEventBus.subscribe(classOf[Detach])(ref)
      clientNode.messageEventBus.subscribe(MessageKeys.MessageResponse)(ref)
      clientNode.messageEventBus.subscribe(classOf[TrackMsgTxId])(ref)
      clientNode.messageEventBus.subscribe(classOf[UnTrackLodgements])(ref)
      clientNode.messageEventBus.subscribe(classOf[TrackSessionRef])(ref)
      clientNode.messageEventBus.subscribe(classOf[TrackLodgements])(ref)
      clientNode.messageEventBus.subscribe(classOf[NewLodgement])(ref)
      //messageEventBus.subscribe (classOf[NewInBoxMessage])(mainNobuRef) <-- TODO
      //TODO Make This actor die on session expiry.
      //MessageDownloadActor(validateBounty, nodeIdentity, userWallet, homeDomain) ! CheckForMessages
      ref
    }

    val sessId = ui.getSession.getSession.getId
    uiActors.updateAndGet( all => {

      all.get(sessId) match {
        case None => all + (sessId -> makeRef(sessId))
        case Some(ref) =>
          ref ! WithUI(ui)
          all
      }
    })(sessId)

  }
}

class UIActor(clientNode: ClientNode, val refs: AtomicRefMap)
    extends Actor
      with OnSessionEnd
      with Notifications
      with PushHelper {


  override def postStop(): Unit = {
    log.info(s"UI Actor {} is down, ${refs.get.size} remain", self.path.name)
    trackingRefs foreach (_ ! PoisonPill)
  }

  private val chainId = clientNode.chain.id


  override def receive: Receive = waitForUI

  private var trackingRefs: Set[ActorRef] = Set.empty
  private var txIds: Set[String] = Set.empty
  private var lodgementsFor: Option[(String, () => Unit)] = None

  private def waitForUI: Receive = {
    case WithUI(ui:UI) =>
      implicit val uiImp = ui
      implicit val sessId = ui.getSession.getSession.getId
      context become (waitForUI orElse sessionEnd(sessId) orElse messages orElse lodgements)

  }

  private def lodgements(implicit ui:UI, sessId: String): Receive = {

    case UnTrackLodgements(`sessId`) =>
      lodgementsFor = None

    case TrackLodgements(`sessId`, who, f) =>
      lodgementsFor = Some(who, f)

    case NewLodgement(nodeId, _) if(lodgementsFor.map (_._1 == nodeId).getOrElse(false)) =>
      push (lodgementsFor.get._2())
  }

  private def messages(implicit ui:UI, sessId: String): Receive = {

    case Terminated(ref) =>
      trackingRefs -= ref

    case TrackSessionRef(`sessId`, ref) =>
      trackingRefs += ref
      context watch ref

    case TrackMsgTxId(`sessId`, txIdStr) =>
       txIds += txIdStr

    case IncomingMessage(`chainId`, MessageKeys.MessageResponse, _, resp: SuccessResponse) if txIds.contains(resp.txIdStr) =>
      push (show(s"Message away!"))
      txIds -= resp.txIdStr

    case IncomingMessage(`chainId`, MessageKeys.MessageResponse, _, resp: FailureResponse) if txIds.contains(resp.txIdStr) =>
      push (showWarn(s"Failed to send message - ${resp.info}"))
      txIds -= resp.txIdStr

  }

}
