package sss.ui.nobu

import java.io.File

import com.vaadin.annotations.{Push, Theme}
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.navigator.{Navigator, ViewChangeListener}
import com.vaadin.server.VaadinRequest
import com.vaadin.ui.UI
import sss.ancillary.{Configure, DynConfig}
import sss.asado.account.NodeIdentity
import sss.asado.nodebuilder.{ConfigNameBuilder, HomeDomainBuilder, NodeConfigBuilder}
import sss.db.Db
import sss.ui.reactor.{ReactorActorSystem, UIReactor}


/**
  * Created by alan on 6/10/16.
  */



@Theme("template")
@Push
class NobuUI extends UI with ViewChangeListener with Configure {

  override def init(vaadinRequest: VaadinRequest): Unit = {

    val uiReactor = UIReactor(this)
    val navigator = new Navigator(this, this)
    navigator.addViewChangeListener(this)

    val keyFolder = config.getString("keyfolder")


    val claimUnlockView = new UnlockClaimView(uiReactor, keyFolder, NobuNode.NodeBootstrap.homeDomain)
    navigator.addView(UnlockClaimView.name, claimUnlockView)


    //TODO REMOVE THIS TO LOG IN PROPERLY
//    val nId = NodeIdentity("alan2", "defaultTag", "password")
//    getSession().setAttribute(UnlockClaimView.identityAttr,nId.id)
//    val nobuNode = NobuNode(uiReactor, nId)
//    val mainView = new NobuMainLayout(uiReactor, nobuNode)
//    navigator.addView(mainView.name, mainView)
//    navigator.navigateTo(mainView.name)
    //TODO REMOVE THIS TO LOG IN PROPERLY

    navigator.navigateTo(UnlockClaimView.name)
  }

  override def afterViewChange(viewChangeEvent: ViewChangeEvent): Unit = Unit

  override def beforeViewChange(viewChangeEvent: ViewChangeEvent): Boolean = {

    (getSession().getAttribute(UnlockClaimView.identityAttr) != null, viewChangeEvent.getViewName == UnlockClaimView.name) match {
      case (false, false) =>
        getNavigator().navigateTo(UnlockClaimView.name); false
      case (false, true) => true
      case (true, _) => true
    }
    //true // <---- WARNING WARNING WARNING!
  }
}


object NobuUI {
  lazy val CRLF = System.getProperty("line.separator")
}




