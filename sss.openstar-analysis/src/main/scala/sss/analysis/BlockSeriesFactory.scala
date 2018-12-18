package sss.analysis

import org.jfree.data.xy.XYSeries
import sss.analysis.BlockSeriesFactory.BlockSeries
import sss.ancillary.LogFactory
import sss.db._

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 11/16/16.
  */
object BlockSeriesFactory {
  case class BlockSeries(coinbaseSeries: XYSeries = new XYSeries("Coinbase", false, false),
                         ledgerBalanceSeries: XYSeries = new XYSeries("Ledger Balance", false, false),
                         txSeries: XYSeries = new XYSeries("Tx", false, false),
                         txPerBlockSeries: XYSeries = new XYSeries("Tx Per Block", false, false),
                         auditCountSeries: XYSeries = new XYSeries("Audit count", false, false)
                         )

}
class BlockSeriesFactory(implicit db:Db) extends AnalysisDb {

  lazy private val analysisTable = db.table(headerTableName)

  def fromMillis(num:Long): Long = {
    Try((num / 1000).toLong) match {
      case Failure(e) => num
      case Success(s) => s
    }
  }

  def pointsFromBlock(prevAnalysis: Analysis, analysis: Analysis, blockSeries: BlockSeries)  {
    blockSeries.coinbaseSeries.addOrUpdate(analysis.analysisHeight, fromMillis(analysis.coinbaseTotal))
    blockSeries.ledgerBalanceSeries.addOrUpdate(analysis.analysisHeight, fromMillis(analysis.balance))
    blockSeries.txSeries.addOrUpdate(analysis.analysisHeight, analysis.txCount)
    blockSeries.txPerBlockSeries.addOrUpdate(analysis.analysisHeight, analysis.txInBlockCount)
    blockSeries.auditCountSeries.addOrUpdate(analysis.analysisHeight, analysis.auditCount)
  }

  def seriesFromBlock(previousBlockAnalysis: Analysis, blockHeight: Long, blockSeries: BlockSeries)(implicit db:Db): BlockSeries = {
    if(Analysis.isAnalysed(blockHeight)) {
      val newPrev = Analysis(blockHeight, Some(previousBlockAnalysis.txOuts))
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
