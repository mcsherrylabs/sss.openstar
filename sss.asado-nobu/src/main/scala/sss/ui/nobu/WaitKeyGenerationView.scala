package sss.ui.nobu

import akka.actor.{ActorRef, Props}
import com.vaadin.navigator.{View, ViewChangeListener}
import com.vaadin.ui._
import sss.asado.{AsadoEvent, Status}
import sss.asado.block.BlockChainLedger.NewBlockId
import sss.asado.block.{BlockClosedEvent, Synchronized}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.peers.PeerManager.PeerConnection
import sss.ui.nobu.NobuUI.Detach
import sss.ui.nobu.StateActor.StateQueryStatus
import sss.ui.nobu.WaitKeyGenerationView.{Update}


import scala.collection.JavaConverters._

object WaitKeyGenerationView {

  case class Update(uiId: Option[String], msg: String) extends AsadoEvent

  val name = "waitKeyGenerationView"
}

class WaitKeyGenerationView(implicit messageEventBus: MessageEventBus
                    ) extends VerticalLayout with View {

  /*private val ref = uiReactor.actorOf(Props(WaitKeyGenerationActor))

  messageEventBus.subscribe(classOf[Detach])(ref)

  val btn = new Button("Generating new key, this may take some time ....")
  btn.setEnabled(false)
  val bar = new ProgressBar()
  bar.setIndeterminate(true)

  setSizeFull()
  setDefaultComponentAlignment(Alignment.MIDDLE_CENTER)
  addComponents(btn, bar)*/


  override def enter(event: ViewChangeListener.ViewChangeEvent): Unit = {}


  /*object WaitKeyGenerationActor extends UIEventActor {

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

      case Detach(Some(uiId)) if (ui.getEmbedId == uiId) =>
        context stop self

      case Update(Some(uiId), msg) if (ui.getEmbedId == uiId)=>
        push(btn.setCaption(msg))

    }
  }*/
}
