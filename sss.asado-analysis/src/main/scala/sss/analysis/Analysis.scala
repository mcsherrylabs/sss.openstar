package sss.analysis




import com.twitter.util.SynchronizedLruMap
import org.joda.time.LocalDateTime
import sss.analysis.Analysis.InOut
import sss.analysis.TransactionHistory.{ExpandedTx, ExpandedTxElement}
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.balanceledger.{Tx, TxIndex, TxInput, TxOutput, _}
import sss.asado.block.{Block, BlockTx}
import sss.asado.contract.{Encumbrance, SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.asado.identityledger._
import sss.asado.ledger._
import sss.db._
import sss.asado.util.ByteArrayEncodedStrOps._

/**
  * Created by alan on 11/3/16.
  */
object Analysis extends AnalysisDb with Logging {



  private lazy val analysisCache = new SynchronizedLruMap[Long, Analysis](100)

  val stateCheckPoint = 2
  val stateAnalysedNoCheckpoint = 1

  def getHeaderTable(implicit db:Db): Table = db.table(headerTableName)

  case class InOut(txIndex: TxIndex, txOut: TxOutput)
  case class AnalysisFromMemory(analysisHeight: Long,
                                coinbaseTotal: Long,
                                txOuts: Seq[InOut],
                                txCount: Long,
                                txInBlockCount: Long,
                                timeBlockOpen: Long,
                                auditCount: Int) extends Analysis


  val blockOneAnalysis = AnalysisFromMemory(1, 0, Seq(), 0, 0, 0, 0)

  def isCoinBase(input: TxInput): Boolean = input.txIndex.txId sameElements(CoinbaseTxId)

  def apply(blockHeight: Long, outs: Option[Seq[InOut]])(implicit db:Db): Analysis = {
    require(blockHeight > 0, s"Blockheight starts at 1, not $blockHeight")
    analysisCache.getOrElseUpdate(blockHeight, {
      if (blockHeight == 1) blockOneAnalysis
      else {
        outs match {
          case Some(outs) => AnalysisFromTables(blockHeight, outs, getHeaderTable)
          case None => AnalysisFromTables(blockHeight, db.table(makeTableName(blockHeight)), getHeaderTable)
        }
      }
    })
  }

  def isCheckpoint(blockHeight: Long)(implicit db:Db): Boolean = {
    if (blockHeight == 1) true
    else getHeaderTable(db).find(idCol -> blockHeight, stateCol -> stateCheckPoint).isDefined
  }

  def isAnalysed(blockHeight: Long)(implicit db:Db): Boolean = {
      getHeaderTable(db).find(idCol -> blockHeight, stateCol -> stateAnalysedNoCheckpoint).isDefined
  }


  def analyse(block:Block, prevAnalysis: Analysis, chainHeight: Long, blockTime: LocalDateTime)(implicit db:Db): Analysis = {

    val transactionHistoryWriter = new TransactionHistoryPersistence()
    var errorCount = 0
    val blockHeight = block.height
    val isCheckpointInterval =
      if(blockHeight < 4) true
      else blockHeight % 4000 == 0

    val auditor = new AnalysisMessages(blockHeight)
    val tableName = makeTableName(blockHeight)

    db.tx {
      auditor.delete
      transactionHistoryWriter.delete(blockHeight)
      getHeaderTable.delete(where(s"$idCol = ?") using blockHeight)

      db.executeSqls(Seq(
        dropTableSql(tableName),
        createTableSql(blockHeight)))
    }

    val table = db.table(tableName)

    def audit(cond: Boolean, msg: String): Unit = if(!cond) {
      errorCount += 1
      auditor.write(msg)
    }

    def write(acc: Analysis): Analysis = {
      db.tx[Analysis] {
        val state = if(isCheckpointInterval) {
          acc.txOuts.foreach { io =>
            table.insert(Map(txIndexCol -> io.txIndex.toBytes, txOutCol -> io.txOut.toBytes))
          }
          stateCheckPoint
        } else stateAnalysedNoCheckpoint

        getHeaderTable.insert(acc.analysisHeight,
          acc.coinbaseTotal,
          acc.txCount,
          acc.balance,
          acc.txInBlockCount,
          acc.timeBlockOpen,
          state,
          acc.auditCount
          )
        if(state == stateCheckPoint) apply(blockHeight, None)
        else apply(blockHeight, Some(acc.txOuts))
      }
    }

    def mapNextOuts(allEntries: Seq[BlockTx], previousAnalysis: Analysis): Analysis = {
      var coinBaseIncrease = 0
      val result: Seq[InOut] = allEntries.foldLeft(previousAnalysis.txOuts)((acc, le) => {

        val e = le.ledgerItem.txEntryBytes.toSignedTxEntry
        audit(e.txId sameElements le.ledgerItem.txId, ((s"${le.index} ${le.ledgerItem.ledgerId} " +
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

            val expandedInElements = tx.ins.map { in =>
              if (isCoinBase(in)) {
                audit(tx.outs.head.amount == 1000, s"Coinbase tx is not 1000, ${tx.outs.head.amount}")
                audit(tx.outs.size == 1, s"Coinbase tx has more than one output, ${tx.outs.size}")
                coinBaseIncrease = coinBaseIncrease + tx.outs.head.amount
                ExpandedTxElement(tx.txId, "coinbase", in.amount)
                //newCoinbases = newCoinbases :+ InOut(TxIndex(tx.txId, 0), tx.outs.head)
              } else {
                val foundOpt = acc.find(_.txIndex == in.txIndex)
                audit(foundOpt.isDefined, s"TxIndex from nowhere ${in.txIndex}")
                ExpandedTxElement(tx.txId, getIdFromEncumbrance(foundOpt.get.txOut.encumbrance), in.amount)
              }
            }

            val newOuts = acc.filterNot(index => tx.ins.exists(_.txIndex == index.txIndex))
            // add the tx outs to the list
            val plusNewOuts = tx.outs.indices.map { i =>
              //audit(tx.outs(i).amount > 0, "Why txOut is 0?")<-because the server charge can be 0
              val newIndx = TxIndex(tx.txId, i)
              val out = tx.outs(i)
              (InOut(newIndx, out), ExpandedTxElement(tx.txId, getIdFromEncumbrance(tx.outs(i).encumbrance), out.amount))
            }
            val expandedOutElements = plusNewOuts.map(_._2)
            val transactionHistory = ExpandedTx(expandedInElements, expandedOutElements, blockTime, blockHeight)
            transactionHistoryWriter.write(transactionHistory)

            plusNewOuts.map(_._1) ++ newOuts
          case x =>
            println(s"Another type of ledger? $x")
            acc
        }
      })
      val txInBlockCount = allEntries.size

      AnalysisFromMemory(block.height, previousAnalysis.coinbaseTotal + coinBaseIncrease,
        result, previousAnalysis.txCount + txInBlockCount, txInBlockCount, 0, errorCount)
    }

    auditor.write(s"Audit of $blockHeight begins .....")

    val accumulator = mapNextOuts(block.entries, prevAnalysis)
    auditor.write(s"Audit of $blockHeight done, writing accumulator .....")
    val written = write(accumulator)

    log.info(s"Wrote accumulator, checkoint=$isCheckpointInterval, " +
      s"balance=${accumulator.balance}, " +
      s"height=${accumulator.analysisHeight}, " +
      s"entries=${accumulator.txOuts.size}, " +
      s"audits=${accumulator.auditCount} .....")

    written
  }


  private def getIdFromEncumbrance(enc: Encumbrance): String = {
    enc match {
      case SingleIdentityEnc(id, minBlockHeight) => id.value
      case SaleOrReturnSecretEnc(returnIdentity,
      claimant,
      hashOfSecret,
      returnBlockHeight) => claimant.value
      case _ => "coinbase"
    }
  }
}

trait Analysis {

  val txOuts: Seq[InOut]
  lazy val balance: Long = txOuts.foldLeft(0)((acc, e) => { acc + e.txOut.amount })
  val coinbaseTotal: Long
  val auditCount: Int
  val txCount: Long
  val txInBlockCount: Long
  val timeBlockOpen: Long
  val analysisHeight: Long

}

trait AnalysisDb {

  val txIndexCol = "txIndex"
  val txOutCol = "txOut"
  val stateCol = "state"
  val coinbaseCol = "coinbase"
  val txCountCol = "txCount"
  val txOutTotalCol = "txOutTotal"
  val txBlockCountCol = "txBlockCount"
  val timeBlockOpenCol = "timeBlockOpen"
  val auditCountCol = "auditCount"
  val idCol = "id"

  def makeTableName( blockHeight: Long) = s"analysis_$blockHeight"
  val headerTableName = s"analysis_header"

  def dropTableSql( tableName: String) = s"DROP TABLE ${tableName} IF EXISTS;"

  def createTableSql( blockHeight: Long) =
    s"""CREATE TABLE ${makeTableName(blockHeight)}
        |($idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
        |$txIndexCol BLOB,
        |$txOutCol BLOB);
        |""".stripMargin


}

object AnalysisFromTables {

  def apply(analysisHeight: Long, txOuts: Seq[InOut], headerTable: View)(implicit db:Db): Analysis = {
    val row = headerTable(analysisHeight)
    new AnalysisFromTable(txOuts, row)
  }
  def apply(analysisHeight: Long, table: View, headerTable: View)(implicit db:Db): Analysis = {
    val row = headerTable(analysisHeight)
    new AnalysisFromTables(table, row)
  }
}

class AnalysisFromTables(table: View, row:Row)(implicit db:Db) extends AnalysisDb with Analysis {

  lazy val txOuts: Seq[InOut] = table.map { row =>
    InOut(row[Array[Byte]](txIndexCol).toTxIndex,
      row[Array[Byte]](txOutCol).toTxOutput)
  }
  override val analysisHeight: Long = row[Long](idCol)
  lazy override val balance: Long =  row[Long](txOutTotalCol)
  override val auditCount: Int =  row[Int](auditCountCol)
  override val txCount: Long = row[Long](txCountCol)
  override val txInBlockCount: Long = row[Long](txBlockCountCol)
  override val timeBlockOpen: Long = row[Long](timeBlockOpenCol)
  override val coinbaseTotal: Long = row[Long](coinbaseCol)

}

class AnalysisFromTable(override val txOuts: Seq[InOut], row:Row)(implicit db:Db) extends AnalysisDb with Analysis {

  override val analysisHeight: Long = row[Long](idCol)
  lazy override val balance: Long =  row[Long](txOutTotalCol)
  override val auditCount: Int =  row[Int](auditCountCol)
  override val txCount: Long = row[Long](txCountCol)
  override val txInBlockCount: Long = row[Long](txBlockCountCol)
  override val timeBlockOpen: Long = row[Long](timeBlockOpenCol)
  override val coinbaseTotal: Long = row[Long](coinbaseCol)

}

