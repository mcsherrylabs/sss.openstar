package sss.ui.nobu

import java.io.File

import com.vaadin.annotations.{Push, Theme}
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.navigator.{Navigator, ViewChangeListener}
import com.vaadin.server.{VaadinRequest, VaadinSession}
import com.vaadin.ui.UI
import sss.asado.AsadoEvent
import sss.ui.nobu.Main.ClientNode
import sss.ui.nobu.NobuUI.Detach
import sss.ui.reactor.UIReactor

/**
  * Created by alan on 6/10/16.
  */
object NobuUI {
  case class Detach(ui: Option[String]) extends AsadoEvent

  lazy val CRLF = System.getProperty("line.separator")
}

@Theme("template")
@Push
class NobuUI(clientNode: ClientNode) extends UI with ViewChangeListener {

  override def init(vaadinRequest: VaadinRequest): Unit = {

    import clientNode.{blockingWorkers, users, messageEventBus, nodeIdentityManager, identityService, globalChainId, db, homeDomain}
    implicit val conf = clientNode.conf
    implicit val currentBlockHeight = () => clientNode.currentBlockHeight()

    VaadinSession.getCurrent().getSession().setMaxInactiveInterval(-1)

    implicit val uiReactor = UIReactor(this)
    val navigator = new Navigator(this, this)
    navigator.addViewChangeListener(this)


    val claimUnlockView = new UnlockClaimView(users, clientNode.buildWallet)
    val waitSyncView = new WaitSyncedView()
    navigator.addView(WaitSyncedView.name, waitSyncView)
    navigator.addView(UnlockClaimView.name, claimUnlockView)
    navigator.addView(WaitKeyGenerationView.name, new WaitKeyGenerationView())

    navigator.navigateTo(UnlockClaimView.name)
  }

  override def afterViewChange(viewChangeEvent: ViewChangeEvent): Unit = Unit

  override def beforeViewChange(viewChangeEvent: ViewChangeEvent): Boolean = {

    (Option(getSession().getAttribute(UnlockClaimView.identityAttr)), viewChangeEvent.getViewName) match {
      case (_, WaitKeyGenerationView.name) => true
      case (_, WaitSyncedView.name) => true
      case (_, UnlockClaimView.name) => true
      case (None, _) =>
        getNavigator().navigateTo(UnlockClaimView.name)
        false
      case (Some(_), _) => true
    }
    //true // <---- WARNING WARNING WARNING!
  }


  override def detach(): Unit = {
    clientNode.messageEventBus publish Detach(Option(this.getEmbedId))
    super.detach()
  }
}





