package sss.analysis

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import akka.agent.Agent
import com.vaadin.ui._
import sss.analysis.DashBoard.Status
import sss.asado.nodebuilder.ClientNode

/**
  * Created by alan on 10/27/16.
  */
class IdentitiesTab(clientNode: ClientNode, status: Agent[Status]) extends VerticalLayout {

  import clientNode.db

  val panel = new Panel("Asado Identities")
  val layout = new FormLayout()
  setSpacing(true)
  setMargin(true)
  layout.setSpacing(true)
  layout.setMargin(true)
  layout.setSizeFull
  panel.setContent(layout)
  panel.setSizeFull
  addComponent(panel)

  val idCount = new AtomicInteger(0)

  update()

  def update() {

    val wallets = new WalletsAnalysis(status.get.lastAnalysis.txOuts)
    layout.removeAllComponents()
    val all = clientNode.identityService.list()
    idCount.set(all.size)

    all foreach { id =>

      val tf = new TextField(id)
      val bal = wallets(id).balance
      val balLnk = new Button(bal.toString)

      val ac = clientNode.identityService.accounts(id)

      tf.setValue(ac.map(_.tag).mkString(","))
      tf.setReadOnly(true)
      //val hl = new VerticalLayout()
      layout.addComponent(tf)
      layout.addComponent(balLnk)

    }
  }

}
