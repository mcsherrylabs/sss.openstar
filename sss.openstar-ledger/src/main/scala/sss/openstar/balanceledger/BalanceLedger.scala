package sss.openstar.balanceledger

import java.util

import sss.ancillary.Logging
import sss.openstar.OpenstarEvent
import sss.openstar.balanceledger.BalanceLedger.NewUtxo
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.common.block.BlockId
import sss.openstar.contract.LedgerContext._
import sss.openstar.contract.{CoinbaseDecumbrance, CoinbaseValidator, LedgerContext, SinglePrivateKey}
import sss.openstar.identityledger.IdentityService
import sss.openstar.ledger._
import sss.openstar.util.ByteArrayEncodedStrOps._
import sss.db.Db
import sss.openstar.account.NodeIdentity

import scala.util.Try

object BalanceLedger {

  case class NewUtxo(txIndx: TxIndex, out: TxOutput) extends OpenstarEvent

  def apply(cbe : CoinbaseValidator,identityService: IdentityService)(implicit db:Db, chainIdMask: GlobalChainIdMask) : BalanceLedger =
    new BalanceLedger(new UTXODBStorage(chainIdMask), cbe, identityService)
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

  private def validateCoinbase(blockHeight: Long,
                               sigs: Seq[Seq[Array[Byte]]],
                               tx: Tx,
                               inputs: Seq[TxInput]): Int = {

    require(inputs.size == 1, "Coinbase can only have 1 input")

    val in = inputs.head
    in.txIndex match {
      case TxIndex(coinbaseTxId, 0) if CoinbaseTxId sameElements coinbaseTxId =>

        log.debug(s"Coinbase validation for height $blockHeight")

        coinbaseValidator.validate(blockHeight, sigs, tx)
        // No need to delete from storage because it's not in storage.
          if (blockHeight % 100 == 0) {
          log.info(s"Balance ledger balance is ${balance} at height $blockHeight, " +
          s"adding ${in.amount} via coinbase")
        }
        in.amount

      case TxIndex(_, _) =>
        throw new IllegalArgumentException(s"Only one coinbase tx allowed ${in.txIndex}.")
    }
  }

  private[balanceledger] def apply(stx: SignedTxEntry, blockId:BlockId): Seq[NewUtxo] = {

    val blockHeight = blockId.blockHeight

    val tx = stx.txEntryBytes.toTx
    storage.inTransaction {
      import tx._
      require(ins.nonEmpty, "Tx has no inputs")
      require(outs.nonEmpty, "Tx has no outputs")

      require(stx.signatures.length == ins.length, "Every input *must* have a specified param sequence, even if it's empty.")
      var totalIn = 0

      if (blockId.txIndex == 1) {
        //this is coinbase
        totalIn += validateCoinbase(blockHeight, stx.signatures, tx, ins)

      } else {
        ins.indices foreach { i =>
          val in = ins(i)
          entry(in.txIndex) match {
            case Some(txOut) =>

              require(txOut.amount == in.amount,
                s"In amount ${in.amount}) must equal out amount ${txOut.amount} "
              )

              totalIn += in.amount
              val context = LedgerContext(contextTemplate + (blockHeightKey -> blockHeight))

              require(txOut.encumbrance.decumber(txId +: stx.signatures(i), context, in.sig),
                "Failed to decumber!"
              )
              storage.delete(in.txIndex)

            case None => throw new IllegalArgumentException(s"${in.txIndex} does not exist.")
          }
        }
      }

      var totalOut = 0
      val newUtxos = outs.indices map { i =>
        val out = outs(i)
        require(out.amount >= 0, "Out amount cannot be negative.")
        totalOut += out.amount
        storage.write(TxIndex(txId, i), out)
        NewUtxo(TxIndex(txId, i), out)
      }
      require(totalOut == totalIn, s"Total out (${totalOut}) *must* equal total in (${totalIn})")

      newUtxos
    }
  }

  override def balance: Int = storage.entries.foldLeft(0)((acc, e) => acc + e.amount )

  override def apply(ledgerItem: LedgerItem, blockId: BlockId): LedgerResult = Try {
    val stx = ledgerItem.txEntryBytes.toSignedTxEntry
    require(util.Arrays.equals(stx.txId, ledgerItem.txId),
      s"The transmitted txId (${ledgerItem.txId.toBase64Str}) " +
        s"does not match the generated TxId (${stx.txId.toBase64Str})"
    )

    apply(stx, blockId)
  }

  override def coinbase(nodeIdentity: NodeIdentity, forBlockHeight: Long, ledgerId: Byte): Option[LedgerItem] = {

    if(coinbaseValidator.rewardPerBlockAmount > 0) {
      val coinbaseAmount = coinbaseValidator.rewardPerBlockAmount
      val minNumBlocksWait = coinbaseValidator.numBlocksInTheFuture
      val ins: Seq[TxInput] = Seq(TxInput(TxIndex(CoinbaseTxId, 0),
        coinbaseAmount,
        CoinbaseDecumbrance(forBlockHeight))
      )

      val outs = Seq(TxOutput(coinbaseAmount,
        SinglePrivateKey(nodeIdentity.publicKey, forBlockHeight + minNumBlocksWait))
      )
      val cb = StandardTx(ins, outs)
      val txSignature = nodeIdentity.sign(cb.txId)
      val stx = SignedTxEntry(cb.toBytes, Seq(Seq(txSignature)))
      Some(LedgerItem(ledgerId, stx.txId, stx.toBytes))

    } else None
  }
}

