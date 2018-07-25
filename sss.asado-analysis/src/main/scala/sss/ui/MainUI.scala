package sss.ui

import com.vaadin.annotations.{Push, Theme}
import com.vaadin.server.VaadinRequest
import com.vaadin.ui._
import sss.analysis.Main
import sss.ui.reactor.{ReactorActorSystem, UIReactor}


/**
  * Created by alan on 8/17/16.
  */
@Theme("valo")
@Push
class MainUI extends UI with ReactorActorSystem {

  override def init(request: VaadinRequest): Unit = {
    setContent(new DashBoard(UIReactor(this), Main.clientNode))
  }
}



