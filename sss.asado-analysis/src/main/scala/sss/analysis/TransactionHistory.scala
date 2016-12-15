package sss.analysis

import sss.asado.balanceledger.BalanceLedger
import sss.asado.block.Block
import sss.asado.ledger._
import sss.asado.balanceledger._
import sss.asado.contract.{Encumbrance, SaleOrReturnSecretEnc, SingleIdentityEnc}
/**
  * Created by alan on 12/15/16.
  */
object TransactionHistory {

  case class ExpandedTxElement(txId: TxId, identity: String, amount: Long)
  case class ExpandedTx(ins: Seq[ExpandedTxElement], outs: Seq[ExpandedTxElement])
}

class TransactionHistory(balanceLedger: BalanceLedger) {

  import TransactionHistory._

  def toExpandedTxStream(block: Block): Stream[ExpandedTx] = {
    block.entries.toStream.map { entry =>
        val e = entry.ledgerItem.txEntryBytes.toSignedTxEntry
        val tx = e.txEntryBytes.toTx
        val expandedIns = tx.ins.map { in =>
          val whoIsSepnding = getIdFromEncumbrance(balanceLedger.entry(in.txIndex).get.encumbrance)
          ExpandedTxElement(tx.txId, whoIsSepnding, in.amount)
        }
      val expandedOuts = tx.outs.map { out =>
          val whoBenefits = getIdFromEncumbrance(out.encumbrance)
          ExpandedTxElement(tx.txId, whoBenefits, out.amount)
        }
      ExpandedTx(expandedIns, expandedOuts)
    }
  }

  private def getIdFromEncumbrance(enc: Encumbrance): String = {
    enc match {
      case SingleIdentityEnc(id, minBlockHeight) => id
      case SaleOrReturnSecretEnc(returnIdentity,
      claimant,
      hashOfSecret,
      returnBlockHeight) => claimant
      case _ => "coinbase"
    }
  }


}
