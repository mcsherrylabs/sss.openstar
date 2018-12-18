package sss.ui

import java.util.concurrent.atomic.AtomicInteger

import akka.agent.Agent
import com.vaadin.ui._
import sss.analysis.Main.ClientNode
import sss.openstar.util.ByteArrayEncodedStrOps._
import sss.ui.DashBoard._
/**
  * Created by alan on 10/27/16.
  */
class IdentitiesTab(clientNode: ClientNode, status: Agent[Status]) extends VerticalLayout {

  val panel = new Panel("Openstar Identities")
  val layout = new VerticalLayout()
  setSpacing(true)
  setMargin(true)
  layout.setSpacing(true)
  layout.setMargin(true)
  layout.setSizeFull
  panel.setContent(layout)
  panel.setSizeFull
  addComponent(panel)

  private lazy val idCount = new AtomicInteger(clientNode.identityService.list().size)

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

  def update(push: ( => Unit) => Unit) {

    val allIds = clientNode.identityService.list()
    val comps = allIds map (createRow(_))

    push {
      layout.removeAllComponents()
      comps foreach (layout.addComponent(_))
      idCount.set(allIds.size)
    }

  }

}
