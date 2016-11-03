package sss.analysis

import sss.analysis.Analysis.InOut
import sss.asado.balanceledger.{TxIndex, TxInput, TxOutput}
import sss.asado.block.Block
import sss.asado.ledger._
import sss.db.Db

/**
  * Created by alan on 11/3/16.
  */

object Analysis {

  case class InOut(txIndex: TxIndex, txOut: TxOutput)

  def analyse(block:Block, previousAnalysis: Analysis): Analysis = {

  }

  def opt(blockHeight:Long): Option[Analysis] = {
    if(blockHeight == 0) Option(apply(blockHeight))
    else {

    }
  }

  def apply(blockHeight:Long): Analysis = {
    if(blockHeight == 0) NullAnalysis
    else {
      new AnalysisImpl(blockHeight)
    }

  }


  def isCoinBase(input: TxInput): Boolean = {
    input.txIndex.txId sameElements(CoinbaseTxId)
  }
}

trait Analysis {
  val txOuts: Seq[InOut] = Seq()
  val balance: Long = txOuts.foldLeft(0)((acc, e) => { acc + e.txOut.amount })
  val coinbaseTotal: Long = 0
}

object NullAnalysis extends Analysis

class AnalysisImpl(blockHeight: Long)(implicit db:Db) extends Analysis {

  private val tableName = s"analysis_$blockHeight"
  private val txIndexCol = "txIndex"
  private val txOutCol = "txOut"
  private val idCol = "id"

  private val createTableSql =
    s"""CREATE TABLE IF NOT EXISTS ${tableName}
        |($idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
        |$txIndexCol BLOB,
        |$txOutCol BLOB);
        |""".stripMargin

  db.executeSql(createTableSql)

  private val table = db.table(tableName)
}
