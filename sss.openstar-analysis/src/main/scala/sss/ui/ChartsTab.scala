package sss.ui

import java.util.concurrent.atomic.AtomicLong

import com.vaadin.data.HasValue.{ValueChangeEvent, ValueChangeListener}
import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui._
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer
import org.jfree.data.xy.DefaultTableXYDataset
import org.vaadin.addon.JFreeChartWrapper
import sss.analysis.BlockSeriesFactory
import sss.analysis.BlockSeriesFactory.BlockSeries
import sss.analysis.Main.ClientNode

import scala.util.{Failure, Try}


/**
  * Created by alan on 11/16/16.
  */
class ChartsTab(clientNode:  ClientNode) extends VerticalLayout {

  import clientNode.db

  val blockSeriesFactory = new BlockSeriesFactory
  val blockSeries = BlockSeries()
  val ds: DefaultTableXYDataset = new DefaultTableXYDataset

  ds.addSeries(blockSeries.txSeries)
  ds.addSeries(blockSeries.coinbaseSeries)
  ds.addSeries(blockSeries.ledgerBalanceSeries)
  ds.addSeries(blockSeries.txPerBlockSeries)
  ds.addSeries(blockSeries.auditCountSeries)

  val y: NumberAxis = new NumberAxis("Txs, Coinbase, etc.")
  val x: NumberAxis = new NumberAxis("Block Height")
  //x.setTickUnit(new NumberTickUnit(100))
  y.setAutoRangeIncludesZero(false)
  x.setAutoRangeIncludesZero(false)
  x.setStandardTickUnits(NumberAxis.createIntegerTickUnits())
  y.setStandardTickUnits(NumberAxis.createIntegerTickUnits())


  x.setAutoRange(true)

  val plot2: XYPlot = new XYPlot(ds, x, y, new DefaultXYItemRenderer())
  //plot2.setForegroundAlpha(0.5f)
  val c: JFreeChart = new JFreeChart(plot2)

  setMargin(true)
  setSpacing(true)

  val lowerPanel = new Panel("Openstar Charts")

  var chart = new JFreeChartWrapper(c)
  chart.setWidth("100%")
  chart.setHeight("500px")

  lowerPanel.setContent(chart)
  addComponent(lowerPanel)
  val btn = new Button("Repaint")
  btn.setWidth("200px")

  btn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      chart.detach()
      chart.attach()
    }
  })

  val xChange = new TextField("X minimum")
  val xMaxChange = new TextField("X maximum")
  val yMinChange = new TextField("Y minimum")
  val yMaxChange = new TextField("Y maximum")

  val resetBtn = new Button("Reset")
  resetBtn.setWidth("200px")

  resetBtn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      x.setAutoRange(true)
      y.setAutoRange(true)
      xChange.setValue("")
      xMaxChange.setValue("")
      yMinChange.setValue("")
      yMaxChange.setValue("")
      chart.detach()
      chart.attach()
    }
  })

  val hLayout = new HorizontalLayout()
  addComponent(hLayout)
  val vLayout1 = new VerticalLayout()
  val vLayout2 = new VerticalLayout()
  val vLayout3 = new VerticalLayout()
  hLayout.setSpacing(true)
  hLayout.setMargin(true)
  hLayout.addComponents(vLayout1, vLayout2, vLayout3)
  vLayout1.setMargin(true)
  vLayout1.setSpacing(true)

  vLayout2.setMargin(true)
  vLayout3.setMargin(true)
  vLayout1.addComponents(btn, resetBtn)
  vLayout2.addComponents(xChange, xMaxChange)
  vLayout3.addComponents(yMinChange, yMaxChange)

  yMaxChange.addValueChangeListener(new ValueChangeListener[String] {
    override def valueChange(event: ValueChangeEvent[String]): Unit = Try {
      if(event.getValue != "") {
        val newUpper = yMaxChange.getValue.toInt
        val r = y.getRange
        y.setRange(r.getLowerBound, newUpper)
      }
    } match {
      case Failure(e) => Notification.show(s"$e")
      case _ =>
    }
  })

  yMinChange.addValueChangeListener(new ValueChangeListener[String] {
    override def valueChange(event: ValueChangeEvent[String]): Unit = Try {
      if(event.getValue != "") {
        val newLower = yMinChange.getValue.toInt
        val r = y.getRange
        y.setRange(newLower, r.getUpperBound)
      }
    } match {
      case Failure(e) => Notification.show(s"$e")
      case _ =>
    }
  })


  xMaxChange.addValueChangeListener(new ValueChangeListener[String] {
    override def valueChange(event: ValueChangeEvent[String]): Unit = Try {
      if(event.getValue != "") {
        val newUpper = xMaxChange.getValue.toInt
        val r = x.getRange
        x.setRange(r.getLowerBound, newUpper)
      }
    } match {
      case Failure(e) => Notification.show(s"$e")
      case _ =>
    }
  })

  xChange.addValueChangeListener(new ValueChangeListener[String] {
    override def valueChange(event: ValueChangeEvent[String]): Unit = Try {
      if(event.getValue != "") {
        val newLower = xChange.getValue.toInt
        val r = x.getRange
        update(newLower)
        x.setRange(newLower, r.getUpperBound)
      }
    } match {
      case Failure(e) => Notification.show(s"$e")
      case _ =>
    }
  })

  val lowXMark = new AtomicLong(Long.MaxValue)

  def update(fromBlock: Long): Unit = {
    if(lowXMark.get() > fromBlock) {
      lowXMark.set(fromBlock)
    }
    blockSeriesFactory.updateSeries(fromBlock, blockSeries)
  }

}
