package sss.asado.block

import java.util.Date

import sss.ancillary.Logging
import sss.asado.block.merkle.MerklePersist._
import sss.asado.block.merkle.{MerklePersist, MerkleTree}
import sss.asado.block.signature.BlockSignatures
import sss.db.{Db, Where}

import scala.collection.mutable
import scala.util.Try


object BlockChain {
  val tableName = "blockchain"

}

class BlockChain(implicit db: Db) extends Logging {

  import BlockChain._
  import MerklePersist.MerklePersister
  import ledger._

  private[block] lazy val blockHeaderTable = db.table(tableName)

  private def lookupBlockHeader(sql: String): Option[BlockHeader] = {
      blockHeaderTable.find(Where(sql)) map (BlockHeader(_))
  }

  def confirm(txId: TxId, height: Long): Unit = Block(height).confirm(txId)


  def genesisBlock(prevHash: String = "GENESIS".padTo(32, "8").toString, merkleRoot: String = "GENESIS".padTo(32, "8").toString): BlockHeader = {
    require(blockHeaderTable.count == 0)
    val genesisHeader = BlockHeader(1, 0, prevHash.substring(0, 32).getBytes, merkleRoot.substring(0,32).getBytes, new Date())
    BlockHeader(blockHeaderTable.insert(genesisHeader.asMap))
  }

  def getUnconfirmed(blockHeight: Long, quorum: Int): Seq[SignedTx] = Block(blockHeight).getUnconfirmed(quorum) map (_._2)

  // use id > 0 to satisfy the where clause. No other reason.
  def lastBlock: BlockHeader = lookupBlockHeader(" id > 0 ORDER BY height DESC LIMIT 1").get

  def blockHeaderOpt(height: Long): Option[BlockHeader] = lookupBlockHeader(s"height = $height")
  def blockHeader(height: Long): BlockHeader = blockHeaderOpt(height).get

  def closeBlock(prevHeader: BlockHeader): Try[BlockHeader] = {

    Try {
      val height = prevHeader.height + 1
      val block = Block(height)

      val hashPrevBlock = prevHeader.hash

      log.debug(s"Size of tx table is ${block.count}")

      db inTransaction {
        val txs = block.entries

        val newBlock = if(txs.nonEmpty) {
          val txIds: IndexedSeq[mutable.WrappedArray[Byte]] = txs.map(_.txId: mutable.WrappedArray[Byte]).toIndexedSeq
          val mt: MerkleTree[mutable.WrappedArray[Byte]] = MerkleTree(txIds)
          mt.persist(MerklePersist.tableName(height))
          new BlockHeader(height, txs.size, hashPrevBlock, mt.root.array, new Date())
        } else new BlockHeader(height, 0, hashPrevBlock, Array(), new Date())

        val newRow = blockHeaderTable.insert(newBlock.asMap)
        log.debug(s"New Block header $newRow")
        BlockHeader(newRow)

      }
    }
  }

  def sign(blockHeader: BlockHeader, nodeId: String): Unit = {
    BlockSignatures(blockHeader.height).add(nodeId)
  }

  def sign(blockHeight: Long, nodeId: String): Unit = sign(blockHeader(blockHeight), nodeId)

  def indexOfBlockSignature(height: Long, nodeId: String): Option[Int] = BlockSignatures(height).indexOfBlockSignature(nodeId)
}