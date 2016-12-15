package sss.ui

import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import sss.asado.nodebuilder.ClientNode

import scala.util.{Failure, Try}


/**
  * Created by alan on 11/16/16.
  */
class QueryTxsTab(clientNode:  ClientNode) extends VerticalLayout {

  import clientNode.db
  // divide the area up into search and results.
  addComponent(createSearchLayout)
  val results = new VerticalLayout
  addComponent(results)


  def createTxLayout: Layout = {
    val txl = new HorizontalLayout
    txl.setWidth("100%")
    txl.setSpacing(true)
    txl.setMargin(true)
    val lhs = new VerticalLayout()
    0 to 3 foreach (_ => lhs.addComponent(new Label("Hello there")))
    val rhs = new VerticalLayout()
    0 to 2 foreach (_ => rhs.addComponent(new Label("Hello there yourself ")))
    lhs.setWidth("50%")
    rhs.setWidth("50%")
    txl.addComponents(lhs, rhs)
    txl
  }

  def createSearchLayout: Layout = {
    val search = new HorizontalLayout
    search.setHeight("100px")
    search.setWidth("700px")

    search.setMargin(true)
    search.setSpacing(true)

    val searchText = new TextField()
    searchText.setWidth("250px")
    search.addComponent(searchText)
    searchText.setValue("asdasd")
    val searchBtn = new Button("Search")

    searchBtn.addClickListener(new ClickListener {
      override def buttonClick(event: ClickEvent) = {
        results.removeAllComponents()
        0 to 3 foreach { _ => results.addComponent(createTxLayout) }
      }
    })

    search.addComponent(searchBtn)

    search.setComponentAlignment(searchText, Alignment.MIDDLE_LEFT)
    search.setComponentAlignment(searchBtn, Alignment.MIDDLE_LEFT)

    search
  }

}
