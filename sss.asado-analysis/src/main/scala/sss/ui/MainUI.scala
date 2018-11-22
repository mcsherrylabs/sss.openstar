package sss.ui

import com.vaadin.annotations.{Push, Theme}
import com.vaadin.server.{VaadinRequest, VaadinSession}
import com.vaadin.ui._
import sss.analysis.Main.ClientNode
import sss.ui.reactor.{ReactorActorSystem, UIReactor}



/**
  * Created by alan on 8/17/16.
  */
@Theme("valo")
@Push
class MainUI(clientNode: ClientNode) extends UI with ReactorActorSystem {

  override def init(request: VaadinRequest): Unit = {
    VaadinSession.getCurrent.getSession.setMaxInactiveInterval(-1)
    setContent(new DashBoard(UIReactor(this), clientNode))
  }
}



