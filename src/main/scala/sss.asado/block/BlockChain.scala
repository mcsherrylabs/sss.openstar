package sss.asado.block

import java.util.Date

import sss.ancillary.Logging
import sss.asado.block.merkle.MerklePersist._
import sss.asado.block.merkle.{MerklePersist, MerkleTree}
import sss.db.{Db, Where}

import scala.collection.mutable
import scala.util.Try

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/15/16.
  */
class BlockChain(implicit db: Db) extends Logging {

  import MerklePersist.MerklePersister
  import ledger._

  val blockTableNamePrefix = "block_"
  val merkleTableNamePrefix = "merkle_"

  private[block] lazy val blockHeaderTable = db.table("blockchain")

  private def lookupBlock(sql: String): Try[BlockHeader] = {
    Try[BlockHeader] {
      blockHeaderTable.find(Where(sql)) match {
        case None => throw new Error("No blocks found!")
        case Some(row) => BlockHeader(row)
      }
    }
  }

  // use id > 0 to satisfy the where clause. No other reason.
  def lastBlock: Try[BlockHeader] = lookupBlock(" id > 0 ORDER BY height DESC LIMIT 1")

  def block(height: Long): Try[BlockHeader] = lookupBlock(s"height = $height")

  def closeBlock: Try[BlockHeader] = {

    lastBlock map { prevHeader =>

      val height = prevHeader.height + 1
      val hashPrevBlock = prevHeader.hash
      lazy val blockTxsTable = db.table(s"$blockTableNamePrefix${height}")

      blockTxsTable inTransaction {
        val txs = blockTxsTable.map { row =>
          row[Array[Byte]]("entry").toSignedTx
        }.toSet

        val txIds: IndexedSeq[mutable.WrappedArray[Byte]] = txs.map(_.txId: mutable.WrappedArray[Byte]).toIndexedSeq
        val mt: MerkleTree[mutable.WrappedArray[Byte]] = MerkleTree(txIds)
        mt.persist(s"$merkleTableNamePrefix$height")

        val newBlock = new BlockHeader(height, txs.size, hashPrevBlock, mt.root.array, new Date())
        val newRow = blockHeaderTable.insert(newBlock.asMap)
        BlockHeader(newRow)
      }

    }
  }

}