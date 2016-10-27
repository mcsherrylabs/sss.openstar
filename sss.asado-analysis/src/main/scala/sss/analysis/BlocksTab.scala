package sss.analysis

import com.vaadin.ui.{Label, VerticalLayout}
import sss.asado.block.Block
import sss.asado.nodebuilder.ClientNode

/**
  * Created by alan on 10/27/16.
  */
class BlocksTab(clientNode: ClientNode) extends VerticalLayout {

  addComponent(new Label("Heellloo?"))

  def update(blockHeight:Long) = {
    val currentBlock = clientNode.bc.block(blockHeight)
  }

}
