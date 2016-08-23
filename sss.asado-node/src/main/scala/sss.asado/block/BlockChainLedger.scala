package sss.asado.block




import sss.ancillary.Logging
import sss.asado.ledger.{ Ledgers, LedgerItem}
import sss.db.Db
import sss.asado.util.ByteArrayEncodedStrOps._



object BlockChainLedger {
  def apply(height: Long)(implicit db: Db, ledgers: Ledgers): BlockChainLedger = new BlockChainLedger(Block(height), ledgers)
}

class BlockChainLedger(block: Block, ledgers: Ledgers) extends Logging {

  val blockHeight = block.height

  /**
    * Write the tx to the block storage, but don't apply it to the UTXO db.
    * Used for distributing the blocks across the network.
    *
    * @param blockTx
    * @return
    */
  def journal(blockTx: BlockTx): BlockChainTx = block.inTransaction[BlockChainTx] {
      block.get(blockTx.ledgerItem.txId) match {
        case Some(blck) =>

          assert(blck.ledgerItem.txId sameElements blockTx.ledgerItem.txId,
            s"Trying to journal index ${blck.index} with ${blockTx.ledgerItem.txId.toBase64Str} " +
              s"when ${blck.ledgerItem.txId.toBase64Str} already exists in that spot")

          BlockChainTx(block.height, BlockTx(blck.index, blck.ledgerItem))

        case None =>
          val index = block.journal(blockTx.index, blockTx.ledgerItem)
          BlockChainTx(block.height, BlockTx(index, blockTx.ledgerItem))
      }
//      Try(block.journal(blockTx.index, blockTx.ledgerItem)) match {
//        case Failure(e: SQLIntegrityConstraintViolationException) =>
//          val blck = block.get(blockTx.ledgerItem.txId).getOrElse(throw e)
//          BlockChainTx(block.height, BlockTx(blck.index, blck.ledgerItem))
//        case Failure(e) => throw e
//        case Success(index) => BlockChainTx(block.height, BlockTx(index, blockTx.ledgerItem))
//      }
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
    commit(block.getUnCommitted)
  }

  def commit: Int = block.inTransaction (commit(block.getUnCommitted))

  private def commit(entries: Seq[BlockTx]): Int = {
    entries foreach { entry =>
      ledgers(entry.ledgerItem, block.height)
      block.commit(entry.index)
    }
    entries.size
  }
  /**
    * Journals and commits a tx at the same time in a transaction.
    *  After this, the tx is irreversible.
    *
    * @param stx
    * @return
    */
  def apply(stx: LedgerItem): BlockChainTx = block.inTransaction[BlockChainTx] {
      ledgers(stx, block.height)
      val index = block.write(stx)
      BlockChainTx(block.height, BlockTx(index, stx))
  }
}
