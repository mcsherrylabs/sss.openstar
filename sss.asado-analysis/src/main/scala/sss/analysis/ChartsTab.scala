package sss.analysis

import com.vaadin.ui.Button.{ClickEvent, ClickListener}
import com.vaadin.ui.{Button, Panel, UI, VerticalLayout}
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.{DateTickUnit, NumberAxis, NumberTickUnit}
import org.jfree.chart.axis.NumberTickUnit
import org.jfree.chart.event.PlotChangeEvent
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.SamplingXYLineRenderer
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer
import org.jfree.data.general.DatasetChangeEvent
import org.jfree.data.xy.DefaultTableXYDataset
import org.vaadin.addon.JFreeChartWrapper
import sss.analysis.BlockSeriesFactory.BlockSeries
import sss.asado.nodebuilder.ClientNode


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
  ds.addSeries(blockSeries.timeSeries)

  val y: NumberAxis = new NumberAxis("Txs, Coinbase, etc.")
  val x: NumberAxis = new NumberAxis("Block Height")
  //x.setTickUnit(new NumberTickUnit(100))
  y.setAutoRangeIncludesZero(false)
  x.setAutoRangeIncludesZero(false)


  x.setAutoRange(true)

  val plot2: XYPlot = new XYPlot(ds, x, y, new DefaultXYItemRenderer())
  //plot2.setForegroundAlpha(0.5f)
  val c: JFreeChart = new JFreeChart(plot2)

  setMargin(true)
  setSpacing(true)

  val lowerPanel = new Panel("Asado Charts")

  var chart = new JFreeChartWrapper(c)
  chart.setWidth("100%")
  chart.setHeight("500px")

  lowerPanel.setContent(chart)
  addComponent(lowerPanel)
  val btn = new Button("Repaint")
  addComponent(btn)

  btn.addClickListener(new ClickListener {
    override def buttonClick(event: ClickEvent): Unit = {
      chart.detach()
      chart.attach()
    }
  })

  def update(): Unit = {
    blockSeriesFactory.updateSeries(2, blockSeries)

  }

}
