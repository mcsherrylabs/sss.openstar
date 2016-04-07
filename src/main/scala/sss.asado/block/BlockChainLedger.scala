package sss.asado.block

import block.{BlockChainTx, BlockTx}
import ledger.{GenisesTx, SignedTx, TxId}
import sss.ancillary.Logging
import sss.asado.ledger.Ledger
import sss.db.Db

import scala.util.Try


case class TxInLedger(txId: TxId) extends RuntimeException("Tx already in ledger")

object BlockChainLedger {
  def apply(height: Long)(implicit db: Db): BlockChainLedger = new BlockChainLedger(Block(height), Ledger())
}

class BlockChainLedger(block: Block, utxo: Ledger) extends Logging {

  val blockHeight = block.height

  def genesis(genesisTx: GenisesTx): Try[BlockChainTx] = {
    Try {
      block.inTransaction[BlockChainTx] {
        block.get(genesisTx.txId) match {
          case Some(s) => BlockChainTx(block.height, BlockTx(s.index, s.signedTx))
          case None =>
            utxo.genesis(genesisTx)
            val index = block.write(genesisTx.txId, SignedTx(genesisTx))
            BlockChainTx(block.height, BlockTx(index, SignedTx(genesisTx)))
        }
      }
    }
  }

  def apply(blockTx: BlockTx): Try[BlockChainTx] = {
    Try {
      block.inTransaction[BlockChainTx] {
        val index = block.write(blockTx.index, blockTx.signedTx.txId, blockTx.signedTx)
        BlockChainTx(block.height, BlockTx(index, blockTx.signedTx))
      }
    }
  }

  def apply(stx: SignedTx): Try[BlockChainTx] = {

    Try {
      block.inTransaction[BlockChainTx] {
        utxo(stx)
        val index = block.write(stx.txId, stx)
        BlockChainTx(block.height, BlockTx(index, stx))
      }
    }
  }

}

