package sss.ui.nobu


import com.vaadin.annotations.{PreserveOnRefresh, Push, Theme, VaadinServletConfiguration}
import com.vaadin.annotations
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.navigator.{Navigator, ViewChangeListener}
import com.vaadin.server.{VaadinRequest, VaadinSession}
import com.vaadin.ui.UI
import sss.ancillary.Logging
import sss.asado.AsadoEvent
import sss.ui.Servlet
import sss.ui.nobu.Main.ClientNode
import sss.ui.nobu.NobuUI.Detach


/**
  * Created by alan on 6/10/16.
  */
object NobuUI {
  case class Detach(ui: Int) extends AsadoEvent

  val SessionAttr = "NOBU"

  case class SessionEnd(str: String)  extends AsadoEvent

  lazy val CRLF = System.getProperty("line.separator")
}

@Theme("template")
@Push
class NobuUI(clientNode: ClientNode) extends UI with ViewChangeListener with Logging {

  log.info("Constructing new NobuUI")

  override def init(vaadinRequest: VaadinRequest): Unit = {

    val sessIntact = Option(getSession().getAttribute(NobuUI.SessionAttr))
    log.info(s"init NobuUI $sessIntact")

    import clientNode.{actorSystem,
      globalChainId,
      send,
      users,
      messageEventBus,
      nodeIdentityManager,
      identityService,
      db,
      homeDomain,
      currentBlockHeightImp,
      confImp
    }



    UIActor(clientNode, this)


    VaadinSession.getCurrent().getSession.getId
    VaadinSession.getCurrent.getSession.setMaxInactiveInterval(-1)


    implicit val ui: UI = this

    val navigator = new Navigator(this, this)
    navigator.addViewChangeListener(this)


    val claimUnlockView = new UnlockClaimView(users, clientNode.buildWallet)
    val waitSyncView = new WaitSyncedView()
    navigator.addView(WaitSyncedView.name, waitSyncView)
    navigator.addView(UnlockClaimView.name, claimUnlockView)
    navigator.addView(WaitKeyGenerationView.name, new WaitKeyGenerationView())

    navigator.navigateTo(WaitSyncedView.name)
  }

  override def afterViewChange(viewChangeEvent: ViewChangeEvent): Unit = Unit

  override def beforeViewChange(viewChangeEvent: ViewChangeEvent): Boolean = {

    (Option(getSession().getAttribute(NobuUI.SessionAttr)), viewChangeEvent.getViewName) match {
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
    clientNode.messageEventBus publish Detach(this.getUIId)
    super.detach()
  }
}





