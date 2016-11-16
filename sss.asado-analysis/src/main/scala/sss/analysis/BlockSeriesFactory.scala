package sss.analysis

import org.jfree.data.xy.XYSeries
import sss.db.Db

/**
  * Created by alan on 11/16/16.
  */
object BlockSeriesFactory {

  def pointsFromBlock(prevAnalysis: Analysis, analysis: Analysis, blockSeries: BlockSeries)  {
    //blockSeries.coinbaseSeries.addOrUpdate(analysis.analysisHeight, analysis.coinbaseTotal)
    //blockSeries.ledgerBalanceSeries.addOrUpdate(analysis.analysisHeight, analysis.balance)
    blockSeries.txSeries.add(analysis.analysisHeight, analysis.txTotal - prevAnalysis.txTotal)
    //blockSeries.timeSeries.addOrUpdate(analysis.analysisHeight, analysis.txTotal- prevAnalysis.txTotal)
  }

  def seriesFromBlock(previousBlockAnalysis: Analysis, blockHeight: Long, blockSeries: BlockSeries)(implicit db:Db): BlockSeries = {
    if(Analysis.isAnalysed(blockHeight)) {
      val newPrev = Analysis(blockHeight)
      pointsFromBlock(previousBlockAnalysis, newPrev, blockSeries)
      seriesFromBlock(newPrev, blockHeight + 1, blockSeries)
    } else blockSeries
  }

  case class BlockSeries(coinbaseSeries: XYSeries = new XYSeries("Coinbase", false, false),
                         ledgerBalanceSeries: XYSeries = new XYSeries("Ledger Balance", false, false),
                         txSeries: XYSeries = new XYSeries("Tx", false, false),
                         timeSeries: XYSeries = new XYSeries("Time", false, false),
                         errorSeries: XYSeries = new XYSeries("Error", false, false))

  def updateSeries(startingFrom: Long, blockSeries: BlockSeries)(implicit db:Db): BlockSeries = {
    if(startingFrom <= 2) seriesFromBlock(Analysis.blockOneAnalysis, 2, blockSeries)
    else seriesFromBlock(Analysis(startingFrom - 1), startingFrom, blockSeries)
  }
}
