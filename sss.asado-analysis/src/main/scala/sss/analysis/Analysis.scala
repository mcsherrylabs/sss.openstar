package sss.analysis


import com.twitter.util.SynchronizedLruMap
import sss.analysis.Analysis.InOut
import sss.analysis.AnalysisMessages.Message
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.balanceledger.{TxIndex, TxInput, TxOutput}
import sss.asado.block.{Block, BlockTx}
import sss.asado.balanceledger._
import sss.asado.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc, SinglePrivateKey}
import sss.asado.identityledger._
import sss.asado.ledger._
import sss.db.{Db, View}
import sss.asado.util.ByteArrayEncodedStrOps._

import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 11/3/16.
  */
object Analysis extends AnalysisDb with Logging {

  private lazy val analysisCache = new SynchronizedLruMap[Long, Analysis](100)

  case class InOut(txIndex: TxIndex, txOut: TxOutput)
  case class AnalysisFromMemory(analysisHeight: Long, coinbaseTotal: Long, txTotal: Long, txOuts: Seq[InOut]) extends Analysis

  val blockOneAnalysis = AnalysisFromMemory(1, 0, 0, Seq())

  def isCoinBase(input: TxInput): Boolean = input.txIndex.txId sameElements(CoinbaseTxId)

  def apply(blockHeight: Long)(implicit db:Db): Analysis = {
    require(blockHeight > 0, s"Blockheight starts at 1, not $blockHeight")
    analysisCache.getOrElseUpdate(blockHeight, {
      if (blockHeight == 1) blockOneAnalysis
      else new AnalysisFromTables(blockHeight, db.table(makeTableName(blockHeight)), db.table(makeHeaderTableName(blockHeight)))
    })
  }

  def isAnalysed(blockHeight: Long)(implicit db:Db): Boolean = {
    Try {
      db.table(makeHeaderTableName(blockHeight))(1).apply[Int](stateCol) == 1
    } match {
      case Failure(e) => false
      case Success(b) => b
    }
  }

  def analyse(block:Block)(implicit db:Db): Analysis = {


    val blockHeight = block.height
    val auditor = new AnalysisMessages(blockHeight)

    auditor.delete

    val tableName = makeTableName(blockHeight)
    val headerTableName = s"analysis_header_$blockHeight"

    db.executeSqls(Seq(
      dropTableSql(headerTableName),
      createHeaderTableSql(blockHeight),
      dropTableSql(tableName),
      createTableSql(blockHeight),
      s"INSERT INTO $headerTableName VALUES (1,0,0,0)"))

    val headerTable = db.table(headerTableName)


    val table = db.table(tableName)

    def audit(cond: Boolean, msg: String): Unit = if(!cond) auditor.write(msg)

    def write(acc: Analysis): Analysis = {
      db.tx[Analysis] {
        acc.txOuts.foreach { io =>
          table.insert(Map(txIndexCol -> io.txIndex.toBytes, txOutCol -> io.txOut.toBytes))
        }

        headerTable.persist(Map(idCol -> 1, stateCol -> 1, coinbaseCol -> acc.coinbaseTotal, txCountCol -> acc.txTotal))
        apply(blockHeight)
      }
    }

    def mapNextOuts(allEntries: Seq[BlockTx], previousAnalysis: Analysis): Analysis = {
      var coinBaseIncrease = 0
      val result: Seq[InOut] = allEntries.foldLeft(previousAnalysis.txOuts)((acc, le) => {

        val e = le.ledgerItem.txEntryBytes.toSignedTxEntry
        audit(!(e.txId sameElements le.ledgerItem.txId), ((s"${le.index} ${le.ledgerItem.ledgerId}" +
          s"${le.ledgerItem.txIdHexStr}=${e.txId.toBase64Str} Tx Entry has different txId to LedgerItem!")))


        le.ledgerItem.ledgerId match {
          case MessageKeys.IdentityLedger =>
            val msg = e.txEntryBytes.toIdentityLedgerMessage
            audit(msg.txId sameElements le.ledgerItem.txId, "Id ledger txId mismatch")
            msg match {
              case Claim(id, pKey) =>
              case x =>
            }
            acc

          case MessageKeys.BalanceLedger =>

            val tx = e.txEntryBytes.toTx
            // are the tx ins in the list of txouts? yes? remove.

            tx.ins.foreach { in =>
              if (isCoinBase(in)) {
                audit(tx.outs.head.amount == 1000, s"Coinbase tx is not 1000, ${tx.outs.head.amount}")
                audit(tx.outs.size == 1, s"Coinbase tx has more than one output, ${tx.outs.size}")
                coinBaseIncrease = coinBaseIncrease + tx.outs.head.amount
                //newCoinbases = newCoinbases :+ InOut(TxIndex(tx.txId, 0), tx.outs.head)
              } else {
                audit(acc.find(_.txIndex == in.txIndex).isDefined, s"TxIndex from nowhere ${in.txIndex}")
              }
            }

            val newOuts = acc.filterNot(index => tx.ins.exists(_.txIndex == index.txIndex))
            // add the tx outs to the list
            val plusNewOuts = tx.outs.indices.map { i =>
              //audit(tx.outs(i).amount > 0, "Why txOut is 0?")<-because the server charge can be 0
              val newIndx = TxIndex(tx.txId, i)
              InOut(newIndx, tx.outs(i))
            }

            plusNewOuts ++ newOuts
          case x =>
            println(s"Another type of ledger? $x")
            acc
        }
      })
      val txCount = allEntries.size
      AnalysisFromMemory(block.height, previousAnalysis.coinbaseTotal + coinBaseIncrease,
        previousAnalysis.txTotal + txCount, result)
    }

    auditor.write(s"Audit of $blockHeight begins .....")
    val accumulator = mapNextOuts(block.entries, apply(blockHeight - 1))
    auditor.write(s"Audit of $blockHeight done, writing accumulator .....")
    val written = write(accumulator)
    log.info(s"Wrote accumulator, (${accumulator.txOuts.size} entries).....")
    written
  }

}

trait Analysis {

  val txOuts: Seq[InOut]
  lazy val balance: Long = txOuts.foldLeft(0)((acc, e) => { acc + e.txOut.amount })
  val coinbaseTotal: Long
  val txTotal: Long
  val analysisHeight: Long

}

trait AnalysisDb {

  val txIndexCol = "txIndex"
  val txOutCol = "txOut"
  val stateCol = "state"
  val coinbaseCol = "coinbase"
  val txCountCol = "txCount"
  val idCol = "id"

  def makeTableName( blockHeight: Long) = s"analysis_$blockHeight"
  def makeHeaderTableName( blockHeight: Long) = s"analysis_header_$blockHeight"

  def dropTableSql( tableName: String) = s"DROP TABLE ${tableName} IF EXISTS;"

  def createTableSql( blockHeight: Long) =
    s"""CREATE TABLE ${makeTableName(blockHeight)}
        |($idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
        |$txIndexCol BLOB,
        |$txOutCol BLOB);
        |""".stripMargin

  def createHeaderTableSql( blockHeight: Long) =
    s"""CREATE TABLE IF NOT EXISTS ${makeHeaderTableName(blockHeight)}
        |($idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
        |$coinbaseCol BIGINT,
        |$txCountCol BIGINT,
        |$stateCol INTEGER);
        |""".stripMargin

}

class AnalysisFromTables(val analysisHeight: Long, table: View, headerTable: View)(implicit db:Db) extends AnalysisDb with Analysis {
  override val txOuts: Seq[InOut] = table.map { row =>
    InOut(row[Array[Byte]](txIndexCol).toTxIndex,
      row[Array[Byte]](txOutCol).toTxOutput)
  }

  override val coinbaseTotal: Long = headerTable(1).apply[Long](coinbaseCol)
  override val txTotal: Long = headerTable(1).apply[Long](txCountCol)
}

