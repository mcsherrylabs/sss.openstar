package sss.asado.block

import sss.ancillary.Logging
import sss.asado.AsadoEvent
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block._
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.util.ByteArrayComparisonOps._
import sss.asado.ledger._
import sss.db.Db

import scala.util.Try



object BlockChainLedger {

  case class NewBlockId(chainId: GlobalChainIdMask, newId: BlockId) extends AsadoEvent

  def apply(height: Long)
           (implicit db: Db, ledgers: Ledgers, chainId: GlobalChainIdMask): BlockChainLedger =
    new BlockChainLedger(Block(height), ledgers)

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
    * A tx that has been
    *
    * @param blockId
    * @return
    */
  def rejected(bTxId: BlockChainTxId): Try[Unit] = {
    for {
      _ <- Try(require(bTxId.height == block.height, s"Cannot reject tx from block ${bTxId.height} in block ${block.height}"))
      _ <- block.reject(bTxId.blockTxId)
    } yield ()
  }

  /**
    * Take all the txs in the block and apply them to the utxo db in order.
    * This is used when syncing from else where.
    * If this goes (permanently) wrong, the network is toast.
    *
    * @param blockId
    */
  def commit(blockId: BlockId): LedgerResult = block.inTransaction {
    Try {
      require(blockId.blockHeight == block.height, s"Cannot apply txs from block ${block.height} to block ${blockId.blockHeight}")
      val count = block.count
      require(blockId.txIndex == block.entries.size, s"There are $count txs in block, but there should be ${blockId.txIndex}, come back later when all are written.")
    } map (_ => commit(block.getUnCommitted))
  }

  def commit: LedgerResult = block.inTransaction (Try(commit(block.getUnCommitted)))

  def commit(entry: BlockTx): LedgerResult = block.inTransaction {

    for {
      events <- ledgers(entry.ledgerItem, BlockId(block.height, entry.index))
      _ = block.commit(entry.index)
    } yield(events)
  }

  private def commit(entries: Seq[BlockTx]): Seq[AsadoEvent] = {
    entries
      .flatMap (bTx => commit(bTx).get)
  }
  /**
    * Journals and commits a tx at the same time in a transaction.
    *  After this, the tx is irreversible.
    *
    * @param stx
    * @return
    */
  def apply(stx: LedgerItem): Try[(BlockChainTx, Seq[AsadoEvent])] = block.inTransaction[(BlockChainTx, Seq[AsadoEvent])] {

    val index = block.write(stx)
    for {
      events <- ledgers(stx, BlockId(block.height, index))
    } yield (BlockChainTx(block.height, BlockTx(index, stx)), events)

  }

  def apply(blockTx: BlockTx): Try[Seq[AsadoEvent]] = block.inTransaction[Seq[AsadoEvent]] {

    val index = block.write(blockTx.index, blockTx.ledgerItem)
    assert(index == blockTx.index, s"Journalled resultant index $index not same as expected index ${blockTx.index}")

    for {
      events <- ledgers(blockTx.ledgerItem, BlockId(block.height, index))
    } yield events

  }

  def validate(blockTx: BlockTx): Try[Seq[AsadoEvent]] = block.validateTx(apply(blockTx).get)

  def validate(stx: LedgerItem): Try[(BlockChainTx, Seq[AsadoEvent])] = block.validateTx(apply(stx).get)



}
