package sss.analysis

import com.vaadin.ui.{Panel, VerticalLayout}
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer

import org.jfree.data.xy.DefaultTableXYDataset
import org.vaadin.addon.JFreeChartWrapper
import sss.analysis.BlockSeriesFactory.BlockSeries
import sss.asado.nodebuilder.ClientNode


/**
  * Created by alan on 11/16/16.
  */
class ChartsTab(clientNode:  ClientNode) extends VerticalLayout {

  import clientNode.db

  val blockSeries = BlockSeries()
  val ds: DefaultTableXYDataset = new DefaultTableXYDataset

//  blockSeries.coinbaseSeries.add(300, 400)
//  blockSeries.coinbaseSeries.add(400, 500, true)

  ds.addSeries(blockSeries.txSeries)
  ds.addSeries(blockSeries.coinbaseSeries)
  ds.addSeries(blockSeries.ledgerBalanceSeries)
  ds.addSeries(blockSeries.timeSeries)

  val y: NumberAxis = new NumberAxis("Txs, Coinbase, etc.")
  val x: NumberAxis = new NumberAxis("Block Height")
  y.setAutoRange(true)
  y.setAutoRangeIncludesZero(false)
  x.setAutoRange(true)
  x.setAutoRangeIncludesZero(false)
  val plot2: XYPlot = new XYPlot(ds, x, y, new DefaultXYItemRenderer())
  //plot2.setForegroundAlpha(0.5f)
  val c: JFreeChart = new JFreeChart(plot2)

  setMargin(true)
  setSpacing(true)



  val lowerPanel = new Panel("Asado Charts")

  val chart = new JFreeChartWrapper(c)
  chart.setSizeFull()

  lowerPanel.setContent(chart)
  addComponent(lowerPanel)

  //update(1440)

  def update(blockHeight: Long): Unit = {
    BlockSeriesFactory.updateSeries(blockHeight, blockSeries)
    chart.markAsDirty()
  }

  def update(lastAnalysis: Analysis): Unit = update(lastAnalysis.analysisHeight)

}
