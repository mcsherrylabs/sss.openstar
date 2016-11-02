package sss.analysis

import com.vaadin.ui.{Label, TextField, VerticalLayout}
import sss.asado.nodebuilder.ClientNode

/**
  * Created by alan on 10/27/16.
  */
class TransactionsTab(clientNode: ClientNode) extends VerticalLayout {

  addComponent(new Label("Og REALLLY?"))

  clientNode.identityService.list() foreach { id =>

    val tf = new TextField(id)
    tf.setValue(clientNode.identityService.accounts(id).map(_.tag).mkString(","))
    tf.setReadOnly(true)
    addComponent(tf)

  }


}
