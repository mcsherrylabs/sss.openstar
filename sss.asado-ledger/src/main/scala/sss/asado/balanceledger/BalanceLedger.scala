package sss.asado.balanceledger

import java.util

import sss.asado.ledger._
import sss.asado.util.ByteArrayEncodedStrOps._

import sss.asado.contract.LedgerContext
import sss.asado.contract.LedgerContext._
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.block.BlockId
import sss.asado.contract.{CoinbaseDecumbrance, CoinbaseValidator, SinglePrivateKey}
import sss.asado.identityledger.IdentityService
import sss.db.Db

object BalanceLedger {
  def apply(cbe : CoinbaseValidator,identityService: IdentityService)(implicit db:Db) : BalanceLedger =
    new BalanceLedger(new UTXODBStorage(), cbe, identityService)
}

trait BalanceLedgerQuery {
  def balance: Int
  def entry(inIndex: TxIndex): Option[TxOutput]
  def map[M](f: (TxOutput) => M): Seq[M]
  def keys: Seq[TxIndex]
}

class BalanceLedger(storage: UTXODBStorage,
                    coinbaseValidator: CoinbaseValidator,
                    identityService: IdentityService
                    ) extends Ledger with BalanceLedgerQuery with Logging {

  private val contextTemplate: Map[String, Any]  = Map(identityServiceKey -> identityService)

  override def entry(inIndex: TxIndex): Option[TxOutput] = storage.get(inIndex)

  /**
    * Used for debug only.
    *
    * @param f
    * @tparam M
    * @return
    */
  def map[M](f: (TxOutput) => M): Seq[M] = storage.entries.map(f)

  def keys: Seq[TxIndex] = storage.keys

  def apply(stx: SignedTxEntry, blockHeight: Long) {

    val tx = stx.txEntryBytes.toTx
    storage.inTransaction {
      import tx._
      require(ins.nonEmpty, "Tx has no inputs")
      require(outs.nonEmpty, "Tx has no outputs")

      require(stx.signatures.length == ins.length, "Every input *must* have a specified param sequence, even if it's empty.")
      var totalIn = 0

      ins.indices foreach { i =>
        val in = ins(i)
        entry(in.txIndex) match {
          case Some(txOut) =>
            require(txOut.amount == in.amount, s"In amount ${in.amount}) must equal out amount ${txOut.amount} ")
            totalIn += in.amount
            val context = LedgerContext(contextTemplate + (blockHeightKey  -> blockHeight))
            require(txOut.encumbrance.decumber(txId +: stx.signatures(i), context, in.sig), "Failed to decumber!")
            storage.delete(in.txIndex)

          case None => in.txIndex match {
            case TxIndex(coinbaseTxId, 0) if CoinbaseTxId sameElements coinbaseTxId =>
              totalIn += in.amount
              coinbaseValidator.validate(blockHeight, stx.signatures, tx)
              // No need to delete from storage becfause it's not in storage.
              if(blockHeight % 100 == 0) {
                log.info(s"Balance ledger balance is ${balance} at height $blockHeight, adding ${in.amount} via coinbase")
              }

            case TxIndex(coinbaseTxId, _) if CoinbaseTxId sameElements coinbaseTxId =>
              throw new IllegalArgumentException(s"Only one coinbase tx allowed ${in.txIndex}.")
            case _ => throw new IllegalArgumentException(s"${in.txIndex} does not exist.")
          }
        }
      }

      var totalOut = 0
      outs.foldLeft(0) { (acc, out) =>
        require(out.amount >= 0, "Out amount cannot be negative.")
        totalOut += out.amount
        storage.write(TxIndex(txId, acc), out)
        acc + 1
      }

      require(totalOut == totalIn, s"Total out (${totalOut}) *must* equal total in (${totalIn})")

    }
  }

  override def balance: Int = storage.entries.foldLeft(0)((acc, e) => acc + e.amount )

  override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {
    val stx = ledgerItem.txEntryBytes.toSignedTxEntry
    require(util.Arrays.equals(stx.txId, ledgerItem.txId), s"The transmitted txId (${ledgerItem.txId.toBase64Str}) does not match the generated TxId (${stx.txId.toBase64Str})")
    apply(stx, blockHeight)
  }

  override def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId, ledgerId: Byte): Option[LedgerItem] = {

    if(coinbaseValidator.rewardPerBlockAmount > 0) {
      val coinbaseAmount = coinbaseValidator.rewardPerBlockAmount
      val minNumBlocksWait = coinbaseValidator.numBlocksInTheFuture
      val ins: Seq[TxInput] = Seq(TxInput(TxIndex(CoinbaseTxId, 0), coinbaseAmount, CoinbaseDecumbrance(blockId.blockHeight)))

      val outs = Seq(TxOutput(coinbaseAmount, SinglePrivateKey(nodeIdentity.publicKey, blockId.blockHeight + minNumBlocksWait)))
      val cb = StandardTx(ins, outs)
      val txSignature = nodeIdentity.sign(cb.txId)
      val stx = SignedTxEntry( cb.toBytes, Seq(Seq(txSignature)))
      Some(LedgerItem(ledgerId, stx.txId, stx.toBytes))
    } else None
  }
}

