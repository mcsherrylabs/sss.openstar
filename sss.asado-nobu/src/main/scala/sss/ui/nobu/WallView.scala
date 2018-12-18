package sss.ui.nobu

import com.vaadin.navigator.View
import sss.ui.milford.design.{Message, Structure, TextMsg}

class WallView extends Structure with View{

  val m = new Message()
  m.addComponent(new TextMsg())
  itemPanel.setContent(m)
}
