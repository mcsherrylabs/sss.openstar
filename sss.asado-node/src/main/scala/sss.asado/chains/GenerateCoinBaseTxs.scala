package sss.asado.chains

import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.TxIndex
import sss.asado.{MessageKeys, Send}
import sss.asado.block._
import sss.asado.chains.BlockCloseDistributorActor.ProcessCoinBaseHook
import sss.asado.common.block.BlockId
import sss.asado.balanceledger._
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.TxWriterActor.InternalLedgerItem
import sss.asado.ledger._
import sss.asado.network.MessageEventBus
import sss.asado.wallet.Wallet
import sss.asado.wallet.WalletPersistence.Lodgement

import scala.util.Try

class GenerateCoinBaseTxs(
              nodeIdentity: NodeIdentity,
              wallet:Wallet
              )(implicit chainId: GlobalChainIdMask,
                send: Send,
                messageEventBus:MessageEventBus,
                ledgers: Ledgers) extends ProcessCoinBaseHook with Logging {

  override def apply(newLastBlock: BlockHeader): Try[Unit] = Try {
    val txs = ledgers.coinbase(nodeIdentity, BlockId(newLastBlock.height, newLastBlock.numTxs))
    if (txs.nonEmpty) {

      txs.foreach { le =>
        // if it's a signed tx entry, send it to the wallet, otherwise ignore.
        // currently (June) there are no other coinbase txs.

        messageEventBus.publish(
          InternalLedgerItem(chainId, le, None)
        )

        if (le.ledgerId == MessageKeys.BalanceLedger) {
          Try(le.txEntryBytes.toSignedTxEntry).map { stx =>
            val outs = stx.txEntryBytes.toTx.outs
            outs.indices foreach { i =>
              wallet.credit(Lodgement(TxIndex(stx.txId, i), outs(i), newLastBlock.height + 1))
              log.info(s"Added ${outs(i).amount} to node ${nodeIdentity.id}'s wallet, balance now ${wallet.balance()} ")
            }
          }
        }
      }
    }
  }
}
