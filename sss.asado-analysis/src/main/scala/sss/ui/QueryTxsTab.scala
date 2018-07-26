package sss.ui

import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.analysis.Main.dateFormat
import sss.analysis.TransactionHistory.{ExpandedTx, ExpandedTxElement}
import sss.analysis.TransactionHistoryQuery


/**
  * Created by alan on 11/16/16.
  */
class QueryTxsTab(transactionHistoryQuery: TransactionHistoryQuery) extends VerticalLayout {

  // divide the area up into search and results.
  addComponent(createSearchLayout)
  val header = new VerticalLayout(paintHeader)
  val results = new VerticalLayout()
  addComponents(header, results)

  def paintHeader(): Component = {
    val surroundPanel = new Panel()
    val outerLayout = new HorizontalLayout(surroundPanel)
    outerLayout.setWidth("100%")
    val timeLbl = new VerticalLayout(new Label("Transaction Time"))
    timeLbl.setWidth("160px")
    timeLbl.setSpacing(true)
    val lhs = new VerticalLayout()
    val rhs = new VerticalLayout()
    val inner = new HorizontalLayout(timeLbl, lhs,rhs)
    inner.setExpandRatio(timeLbl, 0)
    inner.setExpandRatio(lhs, 1)
    inner.setExpandRatio(rhs, 1)
    inner.setWidth("100%")
    surroundPanel.setContent(inner)
    lhs.addComponent(paintHalfHeader("From identity", "Amount"))
    rhs.addComponent(paintHalfHeader("To identity", "Amount"))
    outerLayout
  }

  def paintHalfHeader(a: String, b: String): Component = {
    val result = new HorizontalLayout
    result.setSpacing(true)
    val whoLbl = new Label(a)
    whoLbl.setWidth("200px")
    val amountLbl = new Label(b)
    amountLbl.setWidth("150px")
    result.addComponents(whoLbl, amountLbl)
    result
  }

  def paintExpandedTx(expandedTx: ExpandedTx): Component = {
    val surroundPanel = new Panel()

    val outerLayout = new HorizontalLayout(surroundPanel)
    outerLayout.setWidth("100%")
    val timeLbl = new VerticalLayout(new Label(expandedTx.when.toString(dateFormat)))
    timeLbl.setWidth("160px")
    timeLbl.setSpacing(true)
    val lhs = new VerticalLayout()
    val rhs = new VerticalLayout()
    val inner = new HorizontalLayout(timeLbl, lhs,rhs)
    inner.setExpandRatio(timeLbl, 0)
    inner.setExpandRatio(lhs, 1)
    inner.setExpandRatio(rhs, 1)
    inner.setWidth("100%")
    surroundPanel.setContent(inner)
    lhs.addComponents(expandedTx.ins.map(paintTxElement) : _*)
    rhs.addComponents(expandedTx.outs.map(paintTxElement) : _*)
    outerLayout
  }

  def paintTxElement(element :ExpandedTxElement): Component = {
    val result = new HorizontalLayout
    result.setSpacing(true)
    val whoLbl = new Label(element.identity)
    whoLbl.setWidth("200px")
    val amountLbl = new Label(element.amount.toString)
    amountLbl.setWidth("150px")
    result.addComponents(whoLbl, amountLbl)
    result
  }


  def createSearchLayout: Layout = {
    val search = new HorizontalLayout
    search.setHeight("100px")
    search.setWidth("700px")

    search.setMargin(true)
    search.setSpacing(true)

    val searchText = new TextField()
    searchText.setWidth("250px")

    searchText.setValue("")
    val searchBtn = new Button("Search")

    searchBtn.addClickListener(new ClickListener {
      override def buttonClick(event: ClickEvent) = {
        results.removeAllComponents()
        val searchTextValue = searchText.getValue
        val history: Seq[ExpandedTx] = getHistory(searchTextValue)
        history.foreach(eTx => results.addComponent(paintExpandedTx(eTx)))
        if(results.getComponentCount == 0) Notification.show(s"No results for search '$searchTextValue'")
      }
    })

    val exportBtn = new Button("Export CSV")

    def openCsv(params: String): Unit = {
      UI.getCurrent.getPage.open(params, "_blank")
    }


    exportBtn.addClickListener(new ClickListener {
      override def buttonClick(event: ClickEvent) = {
        val searchTextValue = searchText.getValue
        openCsv(s"/export?id=$searchTextValue")
      }
    })

    search.addComponents(searchText, searchBtn, exportBtn)

    search.setComponentAlignment(searchText, Alignment.MIDDLE_LEFT)
    search.setComponentAlignment(searchBtn, Alignment.MIDDLE_CENTER)
    search.setComponentAlignment(exportBtn, Alignment.MIDDLE_RIGHT)

    search
  }

  private def getHistory(searchTextValue: String) = {
    val history = Option(searchTextValue) match {
      case None => transactionHistoryQuery.list
      case Some("") => transactionHistoryQuery.list
      case Some(id) => transactionHistoryQuery.filter(id)
    }
    history
  }
}
