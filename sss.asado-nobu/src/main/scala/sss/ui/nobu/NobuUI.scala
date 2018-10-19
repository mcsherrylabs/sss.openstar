package sss.ui.nobu

import java.io.File

import com.vaadin.annotations.{Push, Theme}
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.navigator.{Navigator, ViewChangeListener}
import com.vaadin.server.{VaadinRequest, VaadinSession}
import com.vaadin.ui.UI
import sss.ancillary.Configure
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.MessageEventBus
import sss.db.Db
import sss.ui.nobu.Main.ClientNode
import sss.ui.reactor.UIReactor


/**
  * Created by alan on 6/10/16.
  */

@Theme("template")
@Push
class NobuUI(clientNode: ClientNode) extends UI with ViewChangeListener with Configure {

  override def init(vaadinRequest: VaadinRequest): Unit = {


    import clientNode.{messageEventBus, nodeIdentityManager, identityService, globalChainId, db, homeDomain}
    implicit val currentBlockHeight = () => clientNode.currentBlockHeight()

    VaadinSession.getCurrent().getSession().setMaxInactiveInterval(60 * 5)

    implicit val uiReactor = UIReactor(this)
    val navigator = new Navigator(this, this)
    navigator.addViewChangeListener(this)

    val keyFolder = config.getString("keyfolder")
    new File(keyFolder).mkdirs()


    val claimUnlockView = new UnlockClaimView(new UserDirectory(keyFolder), clientNode.buildWallet)
    val waitSyncView = new WaitSyncedView()
    navigator.addView(WaitSyncedView.name, waitSyncView)
    navigator.addView(UnlockClaimView.name, claimUnlockView)


    //TODO REMOVE THIS TO LOG IN PROPERLY
//    val nId = NodeIdentity("alan2", "defaultTag", "password")
//    getSession().setAttribute(UnlockClaimView.identityAttr,nId.id)
//    val nobuNode = NobuNode(uiReactor, nId)
//    val mainView = new NobuMainLayout(uiReactor, nobuNode)
//    navigator.addView(mainView.name, mainView)
//    navigator.navigateTo(mainView.name)
    //TODO REMOVE THIS TO LOG IN PROPERLY

    navigator.navigateTo(WaitSyncedView.name)
  }

  override def afterViewChange(viewChangeEvent: ViewChangeEvent): Unit = Unit

  override def beforeViewChange(viewChangeEvent: ViewChangeEvent): Boolean = {

    (Option(getSession().getAttribute(UnlockClaimView.identityAttr)), viewChangeEvent.getViewName) match {
      case (_, WaitSyncedView.name) => true
      case (_, UnlockClaimView.name) => true
      case (None, _) =>
        getNavigator().navigateTo(UnlockClaimView.name)
        false
      case (Some(_), _) => true
    }
    //true // <---- WARNING WARNING WARNING!
  }
}


object NobuUI {
  lazy val CRLF = System.getProperty("line.separator")
}




