package sss.analysis

import com.vaadin.ui._
import sss.asado.nodebuilder.ClientNode

/**
  * Created by alan on 10/27/16.
  */
class IdentitiesTab(clientNode: ClientNode) extends VerticalLayout {

  val panel = new Panel("Asado Identities")
  val layout = new FormLayout()
  setSpacing(true)
  setMargin(true)
  layout.setSpacing(true)
  layout.setMargin(true)
  panel.setContent(layout)
  addComponent(panel)

  update()

  def update() {

    layout.removeAllComponents()
    clientNode.identityService.list() foreach { id =>

      val tf = new TextField(id)

      tf.setValue(clientNode.identityService.accounts(id).map(_.tag).mkString(","))
      tf.setReadOnly(true)
      layout.addComponent(tf)

    }
  }

}
