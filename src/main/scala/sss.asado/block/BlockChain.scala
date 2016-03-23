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

  def genesisBlock(prevHash: String = "GENESIS".padTo(32, "8").toString, merkleRoot: String = "GENESIS".padTo(32, "8").toString): BlockHeader = {
    require(blockHeaderTable.count == 0)
    val genesisHeader = BlockHeader(1, 0, prevHash.substring(0, 32).getBytes, merkleRoot.substring(0,32).getBytes, new Date())
    BlockHeader(blockHeaderTable.insert(genesisHeader.asMap))
  }

  // use id > 0 to satisfy the where clause. No other reason.
  def lastBlock: Try[BlockHeader] = lookupBlock(" id > 0 ORDER BY height DESC LIMIT 1")

  def block(height: Long): Try[BlockHeader] = lookupBlock(s"height = $height")

  def closeBlock(prevHeader: BlockHeader): Try[BlockHeader] = {

    Try {
      val height = prevHeader.height + 1
      val blockTxsTable = db.table(s"$blockTableNamePrefix${height}")
      val hashPrevBlock = prevHeader.hash

      log.debug(s"Size of tx table is ${blockTxsTable.count}")

      blockTxsTable inTransaction {
        val txs = blockTxsTable.map { row =>
          row[Array[Byte]]("entry").toSignedTx
        }.toSet

        val newBlock = if(txs.size > 0) {
          val txIds: IndexedSeq[mutable.WrappedArray[Byte]] = txs.map(_.txId: mutable.WrappedArray[Byte]).toIndexedSeq
          val mt: MerkleTree[mutable.WrappedArray[Byte]] = MerkleTree(txIds)
          mt.persist(s"$merkleTableNamePrefix$height")
          new BlockHeader(height, txs.size, hashPrevBlock, mt.root.array, new Date())
        } else new BlockHeader(height, 0, hashPrevBlock, Array(), new Date())

        val newRow = blockHeaderTable.insert(newBlock.asMap)
        BlockHeader(newRow)
      }
    }
  }

}