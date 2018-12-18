package sss.ui

import org.scalatra.{Ok, ScalatraServlet}
import sss.analysis.TransactionHistory.{ExpandedTx, ExpandedTxElement}
import sss.analysis.TransactionHistoryQuery

import scala.language.postfixOps

/**
  * Created by alan on 5/7/16.
  */
class ExportServlet(transactionHistoryQuery: TransactionHistoryQuery) extends ScalatraServlet {


  import sss.analysis.Main.dateFormat
  val crlf = System.getProperty("line.separator")
  val padWidth = 100

  def makeString(optInElement: Option[ExpandedTxElement], optOutElement: Option[ExpandedTxElement]): String = (optInElement, optOutElement) match {
    case (None, Some(el)) => s",,,${el.identity},${el.amount}"
    case (Some(el), None) => s",${el.identity},${el.amount},,"
    case (Some(el), Some(el2)) => s",${el.identity},${el.amount},${el2.identity},${el2.amount}"
    case (None, None) => ",,,,"
  }

  private def toCsv(expandedTx: ExpandedTx, txIndex: Int): String = {
    val insSize = expandedTx.ins.size
    val outsSize = expandedTx.outs.size
    val biggest = if(insSize > outsSize) insSize else outsSize
    (0 until biggest).map { i =>
      s"$txIndex," + expandedTx.when.toString(dateFormat) + makeString(expandedTx.ins.lift(i), expandedTx.outs.lift(i))
    }.mkString(crlf)
  }

  get("/") {
    params.get("id") match {
      case None | Some("") =>
        val all = transactionHistoryQuery.list
        val strs = all.indices.map(i => toCsv(all(i), i))
        Ok(strs.mkString(crlf))
      case Some(id) =>
        val all = transactionHistoryQuery.filter(id)
        val strs = all.indices.map(i => toCsv(all(i), i))
        Ok(strs.mkString(crlf))
      }
    }

}
