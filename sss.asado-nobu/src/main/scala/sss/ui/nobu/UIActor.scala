package sss.ui.nobu

import java.util.concurrent.atomic.AtomicReference

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import com.vaadin.ui.UI
import sss.asado.block.{NotSynchronized, Synchronized}
import sss.asado.message.{FailureResponse, MessageDownloadActor, SuccessResponse}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.wallet.UtxoTracker.NewLodgement
import sss.asado._
import sss.asado.account.NodeIdentity
import sss.asado.message.MessageDownloadActor.CheckForMessages
import sss.asado.state.HomeDomain
import sss.asado.wallet.Wallet
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

  case class TrackSessionRef(sessId: String,
                             ref: ActorRef) extends AsadoEvent

  case class UnTrackLodgements(sessId: String) extends AsadoEvent
  case class TrackLodgements(sessId: String, who: UniqueNodeIdentifier, f: () => Unit) extends AsadoEvent
  case class UnSubscribe(cls: Class[_], uiId: String) extends AsadoEvent
  case class TrackMsgTxId(sessId: String, txIdStr: String) extends AsadoEvent

  def apply(clientNode: ClientNode, ui: UI)(implicit as: ActorSystem): ActorRef = {

    def makeRef(sessId: String): ActorRef = {
      val ref = as.actorOf(Props(classOf[UIActor], clientNode, uiActors, ui), s"UIActor${sessId}")
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
        case _ => all
      }
    })(sessId)

  }

}

class UIActor(clientNode: ClientNode, val refs: AtomicRefMap)(implicit ui: UI)
    extends Actor
      with OnSessionEnd
      with Notifications
      with PushHelper {


  override def postStop(): Unit = {
    log.info(s"UI Actor $sessId is down, ${refs.get.size} remain")
    trackingRefs foreach (_ ! PoisonPill)
  }

  private val chainId = clientNode.chain.id
  private val sessId = ui.getSession.getSession.getId

  override def receive: Receive = sessionEnd(sessId) orElse messages orElse lodgements

  private var trackingRefs: Set[ActorRef] = Set.empty
  private var txIds: Set[String] = Set.empty
  private var lodgementsFor: Option[(String, () => Unit)] = None

  private def lodgements: Receive = {

    case UnTrackLodgements(`sessId`) =>
      lodgementsFor = None

    case TrackLodgements(`sessId`, who, f) =>
      lodgementsFor = Some(who, f)

    case NewLodgement(nodeId, _) if(lodgementsFor.map (_._1 == nodeId).getOrElse(false)) =>
      push (lodgementsFor.get._2())
  }

  private def messages: Receive = {

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
