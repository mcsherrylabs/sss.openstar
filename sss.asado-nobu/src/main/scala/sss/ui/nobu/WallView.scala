package sss.ui.nobu

import com.vaadin.navigator.View
import sss.ui.milford.design.{Message, TextMsg}

class WallView extends Message with View{

  addComponent(new TextMsg())
}
