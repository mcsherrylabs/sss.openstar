package sss.asado.block

import java.sql.SQLIntegrityConstraintViolationException

import block.{BlockChainTx, BlockId, BlockTx}
import ledger.{GenisesTx, SignedTx}
import sss.ancillary.Logging
import sss.asado.ledger.Ledger
import sss.db.Db

import scala.util.{Failure, Success, Try}


object BlockChainLedger {
  def apply(height: Long)(implicit db: Db): BlockChainLedger = new BlockChainLedger(Block(height), Ledger())
}

class BlockChainLedger(block: Block, utxo: Ledger) extends Logging {

  val blockHeight = block.height

  def genesis(genesisTx: GenisesTx): BlockChainTx = {
      block.inTransaction[BlockChainTx] {
        block.get(genesisTx.txId) match {
          case Some(s) => BlockChainTx(block.height, BlockTx(s.index, s.signedTx))
          case None =>
            utxo.genesis(genesisTx)
            val index = block.writeCommitted(genesisTx.txId, SignedTx(genesisTx))
            BlockChainTx(block.height, BlockTx(index, SignedTx(genesisTx)))
        }
      }
  }

  /**
    * Write the tx to the block storage, but don't apply it to the UTXO db.
    * Used for distributing the blocks across the network.
    *
    * @param blockTx
    * @return
    */
  def journal(blockTx: BlockTx): BlockChainTx = block.inTransaction[BlockChainTx] {
      Try(block.write(blockTx.index, blockTx.signedTx.txId, blockTx.signedTx)) match {
        case Failure(e: SQLIntegrityConstraintViolationException) =>
          val blck = block.get(blockTx.signedTx.txId).getOrElse(throw e)
          BlockChainTx(block.height, BlockTx(blck.index, blck.signedTx))
        case Failure(e) => throw e
        case Success(index) => BlockChainTx(block.height, BlockTx(index, blockTx.signedTx))
      }
  }

  /**
    * Take all the txs in the block and apply them to the utxo db in order.
    * This is used when syncing from else where.
    * If this goes (permanently) wrong, the network is toast.
    *
    * @param blockId
    */
  def commit(blockId: BlockId): Unit = block.inTransaction {
    require(blockId.blockHeight == block.height, s"Cannot apply txs from block ${block.height} to block ${blockId.blockHeight}")
    val count = block.count
    require(blockId.numTxs == block.entries.size, s"There are $count txs in blocks, but there should be ${blockId.numTxs}, come back later when all are written.")
    block.entries foreach { entry =>
      utxo(entry.signedTx)
      block.commit(entry.index)
    }
  }

  /**
    * Journals and commits a tx at the same time in a transaction.
    *  After this, the tx is irreversible.
    *
    * @param stx
    * @return
    */
  def apply(stx: SignedTx): BlockChainTx = block.inTransaction[BlockChainTx] {
      utxo(stx)
      val index = block.writeCommitted(stx.txId, stx)
      BlockChainTx(block.height, BlockTx(index, stx))
  }
}
