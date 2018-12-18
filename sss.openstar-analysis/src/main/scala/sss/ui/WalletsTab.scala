package sss.ui

import akka.agent.Agent
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.analysis.{WalletAnalysis, WalletsAnalysis}
import sss.analysis.Main.ClientNode
import sss.ui.DashBoard._

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


  private def createHeader(detailLayout: Layout, wallet: WalletAnalysis): Layout = {

    val rowLayout = new HorizontalLayout()
    rowLayout.setSpacing(true)
    rowLayout.setHeight("80px")
    rowLayout.setDefaultComponentAlignment(Alignment.MIDDLE_LEFT)

    val idLbl = new Label("Identity")
    val balLbl = new Label("Balance")

    val tf = new Button(wallet.identity)
    tf.setWidth("150px")
    val bal = wallet.balance
    val balLnk = new Button(bal.toString)
    tf.setEnabled(false)
    rowLayout.addComponents(idLbl, tf, balLbl, balLnk)


    def repaintDetailPanel(showEncdetails: Boolean = true): Unit = {
      detailLayout.setVisible(true)

      wallet.inOuts.foreach { io =>
        val hl = new HorizontalLayout()
        hl.setSpacing(true)
        val indexLbl = new Label(io.txIndex.toString)
        hl.addComponent(indexLbl)
        val amountLbl = new Label(io.txOut.amount.toString)
        hl.addComponent(amountLbl)
        if(showEncdetails) {
          val txLbl = new Label(io.txOut.encumbrance.toString)
          hl.addComponent(txLbl)
        }
        detailLayout.addComponent(hl)
      }

      val btnLayout = new HorizontalLayout
      btnLayout.setSpacing(true)
      btnLayout.setMargin(true)
      val btnClose = new Button("Close")
      val hideDetails = new Button("Show TxIndex only")
      hideDetails.setEnabled(showEncdetails)
      btnLayout.addComponents(btnClose, hideDetails)
      detailLayout.addComponent(btnLayout)

      hideDetails.addClickListener(new ClickListener {
        override def buttonClick(event: ClickEvent): Unit = {
          detailLayout.removeAllComponents()
          repaintDetailPanel(false)
        }
      })

      btnClose.addClickListener(new ClickListener {
        override def buttonClick(event: ClickEvent): Unit = {
          detailLayout.removeAllComponents()
          detailLayout.setVisible(false)
          balLnk.setEnabled(true)
        }
      })

    }

    balLnk.addClickListener(new ClickListener {
      override def buttonClick(event: ClickEvent): Unit = {
        balLnk.setEnabled(false)
        repaintDetailPanel()
      }
    })
    //val hl = new VerticalLayout()
    rowLayout
  }

  private def createRow(wallet: WalletAnalysis): Component = {

    val enclosingLayout = new VerticalLayout()
    val detailLayout = new VerticalLayout()
    val headerLayout = createHeader(detailLayout, wallet)
    enclosingLayout.addComponent(headerLayout)
    enclosingLayout.addComponent(detailLayout)
    enclosingLayout
  }

  def update(push: ( => Unit) => Unit) {

    val wallets = new WalletsAnalysis(status.get.lastAnalysis.txOuts)
    var totalBalance = 0l
    val components = wallets.sortedById.keys map { id =>
      val wallet = wallets(id)
      totalBalance += wallet.balance
      createRow(wallet)
    }

    push {
      layout.removeAllComponents()
      layout.addComponents(components.toSeq: _*)
      panel.setCaption(s"Openstar Wallets - $totalBalance accounted for")
    }
  }

}
