package sss.analysis

import org.jfree.data.xy.XYSeries
import sss.analysis.BlockSeriesFactory.BlockSeries
import sss.ancillary.LogFactory
import sss.db._

/**
  * Created by alan on 11/16/16.
  */
object BlockSeriesFactory {
  case class BlockSeries(coinbaseSeries: XYSeries = new XYSeries("Coinbase", false, false),
                         ledgerBalanceSeries: XYSeries = new XYSeries("Ledger Balance", false, false),
                         txSeries: XYSeries = new XYSeries("Tx", false, false),
                         timeSeries: XYSeries = new XYSeries("Time", false, false),
                         errorSeries: XYSeries = new XYSeries("Error", false, false))

}
class BlockSeriesFactory(implicit db:Db) extends AnalysisDb {

  lazy private val analysisTable = db.table(headerTableName)

  def pointsFromBlock(prevAnalysis: Analysis, analysis: Analysis, blockSeries: BlockSeries)  {
    blockSeries.coinbaseSeries.addOrUpdate(analysis.analysisHeight, analysis.coinbaseTotal)
    blockSeries.ledgerBalanceSeries.addOrUpdate(analysis.analysisHeight, analysis.balance)
    LogFactory.log.info(s"${analysis.analysisHeight} ${analysis.balance - prevAnalysis.balance}")
    blockSeries.txSeries.addOrUpdate(analysis.analysisHeight, analysis.balance - prevAnalysis.balance)
    blockSeries.timeSeries.addOrUpdate(analysis.analysisHeight, analysis.txInBlockCount- prevAnalysis.txInBlockCount)
  }

  def seriesFromBlock(previousBlockAnalysis: Analysis, blockHeight: Long, blockSeries: BlockSeries)(implicit db:Db): BlockSeries = {
    if(Analysis.isAnalysed(blockHeight)) {
      val newPrev = Analysis(blockHeight)
      pointsFromBlock(previousBlockAnalysis, newPrev, blockSeries)
      seriesFromBlock(newPrev, blockHeight + 1, blockSeries)
    } else blockSeries
  }


  def updateSeries(startingFrom: Long, blockSeries: BlockSeries)(implicit db:Db): BlockSeries = {

    val rows = analysisTable.filter(where(s"$idCol >= ?") using startingFrom - 1)
    val analyses = rows map { row =>
      new AnalysisFromTables(db.table(makeTableName(row[Long](idCol))), row)
    }

    analyses.sliding(2, 1).foreach { byTwo =>
      if(byTwo.size == 2) pointsFromBlock(byTwo(0), byTwo(1), blockSeries)
    }
    blockSeries
  }
}
