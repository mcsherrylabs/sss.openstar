package sss.analysis

import java.util.concurrent.atomic.AtomicInteger

import akka.agent.Agent
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.analysis.DashBoard.Status
import sss.asado.nodebuilder.ClientNode


/**
  * Created by alan on 10/27/16.
  */
class WalletsTab(clientNode: ClientNode, status: Agent[Status]) extends VerticalLayout {


  val panel = new Panel()
  val layout = new VerticalLayout()
  setSpacing(true)
  setMargin(true)
  layout.setSpacing(true)
  layout.setMargin(true)
  layout.setSizeFull
  panel.setContent(layout)
  panel.setSizeFull
  addComponent(panel)

  update()

  private def createHeader(wallet: WalletAnalysis): (Layout,Layout) = {
    val detailLayout = new VerticalLayout()

    val rowLayout = new HorizontalLayout()
    rowLayout.setHeight("80px")
    val tf = new Button(wallet.identity)
    rowLayout.addComponent(tf)
    tf.setWidth("150px")
    val bal = wallet.balance
    val balLnk = new Button(bal.toString)
    rowLayout.addComponent(balLnk)

    val btnClose = new Button("Close")
    btnClose.addClickListener(new ClickListener {
      override def buttonClick(event: ClickEvent): Unit = {
        btnClose.setVisible(false)
        detailLayout.removeAllComponents()
      }
    })
    rowLayout.addComponent(btnClose)
    btnClose.setVisible(false)

    tf.addClickListener(new ClickListener {
      override def buttonClick(event: ClickEvent): Unit = {
        btnClose.setVisible(true)
        wallet.inOuts.foreach { io =>
          val hl = new HorizontalLayout()
          hl.setSpacing(true)
          hl.addComponent(new Label(io.txIndex.toString))
          hl.addComponent(new Label(io.txOut.amount.toString))
          detailLayout.addComponent(hl)
        }

      }
    })
    //val hl = new VerticalLayout()
    (detailLayout, rowLayout)
  }

  private def createRow(wallet: WalletAnalysis): Component = {

    val enclosingLayout = new VerticalLayout()

    val headerLayoutTuple = createHeader(wallet)
    enclosingLayout.addComponent(headerLayoutTuple._1)
    enclosingLayout.addComponent(headerLayoutTuple._2)
    enclosingLayout
  }

  def update() {

    val wallets = new WalletsAnalysis(status.get.lastAnalysis.txOuts)
    layout.removeAllComponents()

    var totalBalance = 0l

    wallets.sortedById.keys foreach { id =>

      val wallet = wallets(id)
      totalBalance += wallet.balance
      layout.addComponent(createRow(wallet))
    }
    panel.setCaption(s"Asado Wallets - $totalBalance")
  }

}
