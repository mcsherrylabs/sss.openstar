package sss.analysis

import java.util.concurrent.atomic.AtomicInteger

import akka.agent.Agent
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.analysis.DashBoard.Status
import sss.asado.nodebuilder.ClientNode
import sss.asado.util.ByteArrayEncodedStrOps._

/**
  * Created by alan on 10/27/16.
  */
class IdentitiesTab(clientNode: ClientNode, status: Agent[Status]) extends VerticalLayout {

  val panel = new Panel("Asado Identities")
  val layout = new VerticalLayout()
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

  private def createHeader(detailLayout: Layout, id: String): Layout = {

    val rowLayout = new HorizontalLayout()
    rowLayout.setSpacing(true)
    rowLayout.setHeight("80px")
    rowLayout.setDefaultComponentAlignment(Alignment.MIDDLE_LEFT)

    val idLbl = new Label("Identity")

    val allAcs = clientNode.identityService.accounts(id)
    val idBtn = new Button(id)
    idBtn.setWidth("150px")
    idBtn.setEnabled(false)
    rowLayout.addComponents(idLbl, idBtn)

    detailLayout.setVisible(true)

    allAcs foreach { ac =>
      val hl = new HorizontalLayout()
      hl.setSpacing(true)
      val pKlbl = new Label(ac.account.publicKey.toBase64Str)
      val tagLbl = new Label(ac.tag)
      hl.addComponents(pKlbl, tagLbl)
      detailLayout.addComponent(hl)
    }
    rowLayout
  }

  private def createRow(id: String): Component = {

    val enclosingLayout = new VerticalLayout()
    val detailLayout = new VerticalLayout()
    val headerLayout = createHeader(detailLayout, id)
    enclosingLayout.addComponent(headerLayout)
    enclosingLayout.addComponent(detailLayout)
    enclosingLayout
  }

  def update() {

    layout.removeAllComponents()
    val allIds = clientNode.identityService.list()
    idCount.set(allIds.size)

    allIds foreach { id =>

      layout.addComponent(createRow(id))
    }

  }

}
