package sss.analysis


import org.joda.time.LocalDateTime
import sss.asado.MessageKeys
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.block.{Block, BlockTx}
import sss.asado.ledger._
import sss.asado.balanceledger._
import sss.asado.contract.{Encumbrance, NullEncumbrance, SaleOrReturnSecretEnc, SingleIdentityEnc}
/**
  * Created by alan on 12/15/16.
  */
object TransactionHistory {

  case class ExpandedTxElement(txId: TxId, identity: String, amount: Long) {
    lazy val txIdBase64Str = txId.toBase64Str

    override def equals(o: scala.Any): Boolean = o match {
      case other: ExpandedTxElement =>
        other.txIdBase64Str == txIdBase64Str && other.amount == amount && other.identity == identity
      case _ => false
    }

    override def hashCode(): Int = txIdBase64Str.hashCode + amount.hashCode + identity.hashCode
  }

  case class ExpandedTx(ins: Seq[ExpandedTxElement], outs: Seq[ExpandedTxElement],
                        when: LocalDateTime, blockHeight: Long)

  /*def toExpandedTxStream(analysis: Analysis, block: Block, when: LocalDateTime): Stream[Option[ExpandedTx]] = {

    block.entries.toStream.map { entry =>
        if(entry.ledgerItem.ledgerId != MessageKeys.BalanceLedger) None
        else {
          val e = entry.ledgerItem.txEntryBytes.toSignedTxEntry
          val tx = e.txEntryBytes.toTx
          val expandedIns = tx.ins.map { in =>
            val whoIsSepnding = if(isCoinBase(in)) "coinbase"
            else {
              val enc = analysis.txOuts.find(_.txIndex == in.txIndex) match {
                case None =>
                  block.entries.find(blockTx => blockTx.ledgerItem.txId == in.txIndex.txId).get
                  NullEncumbrance
                case Some(x) => x.txOut.encumbrance
              }
              getIdFromEncumbrance(enc)
            }
            ExpandedTxElement(tx.txId, whoIsSepnding, in.amount)
          }
        val expandedOuts = tx.outs.map { out =>
            val whoBenefits = getIdFromEncumbrance(out.encumbrance)
            ExpandedTxElement(tx.txId, whoBenefits, out.amount)
          }
        Some(ExpandedTx(expandedIns, expandedOuts, when))
      }
    }
  }
  */



}
