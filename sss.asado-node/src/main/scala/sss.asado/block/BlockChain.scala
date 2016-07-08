package sss.asado.block

import java.util.Date


import com.twitter.util.SynchronizedLruMap
import scorex.crypto.signatures.SigningFunctions.{PublicKey, Signature}
import sss.ancillary.Logging
import sss.asado.block.merkle.MerklePersist._
import sss.asado.block.merkle.{MerklePersist, MerkleTree}
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.account.PublicKeyAccount
import sss.db.{Db, Where}

import scala.collection.mutable

trait BlockChainGenesis {
  def genesisBlock(prevHash: String = "GENESIS".padTo(32, "8").toString, merkleRoot: String = "GENESIS".padTo(32, "8").toString): BlockHeader
}

trait BlockChainSignatures {
  this : BlockChain =>
  def addSignature(height: Long, signature: Signature, signersPublicKey: PublicKey, nodeId: String): BlockSignature
  def indexOfBlockSignature(height: Long, nodeId: String): Option[Int]
  def signatures(height: Long, maxToReturn: Int): Iterable[BlockSignature]
}

trait BlockChainTxConfirms {
  def confirm(blockChainTxId: BlockChainTxId): Unit
  def getUnconfirmed(blockHeight: Long, quorum: Int): Seq[BlockChainTx]
}

trait BlockChain {
  def block(blockHeight: Long): Block
  def lastBlockHeader: BlockHeader
  def blockHeaderOpt(height: Long): Option[BlockHeader]
  final def blockHeader(height: Long): BlockHeader = blockHeaderOpt(height).get
  def closeBlock(prevHeader: BlockHeader): BlockHeader
}

object BlockChainImpl {
  val tableName = "blockchain"
  private lazy val blockHeaderCache = new SynchronizedLruMap[Long, BlockHeader](100)
}

class BlockChainImpl(implicit db: Db) extends BlockChain
  with BlockChainSignatures
  with BlockChainGenesis
  with BlockChainTxConfirms
  with Logging {

  import BlockChainImpl._

  private lazy val blockHeaderTable = db.table(tableName)

  private def lookupBlockHeader(sql: String): Option[BlockHeader] = {
    blockHeaderTable.find(Where(sql)) map (BlockHeader(_))
  }

  def confirm(blockChainTxId: BlockChainTxId): Unit = Block(blockChainTxId.height).confirm(blockChainTxId.blockTxId)

  def genesisBlock(prevHash: String = "GENESIS".padTo(32, "8").toString, merkleRoot: String = "GENESIS".padTo(32, "8").toString): BlockHeader = {
    require(blockHeaderTable.count == 0)
    val genesisHeader = BlockHeader(1, 0, prevHash.substring(0, 32).getBytes, merkleRoot.substring(0, 32).getBytes, new Date())
    BlockHeader(blockHeaderTable.insert(genesisHeader.asMap))
  }

  def getUnconfirmed(blockHeight: Long, quorum: Int): Seq[BlockChainTx] =
    Block(blockHeight).getUnconfirmed(quorum) map (pair => BlockChainTx(blockHeight, pair._2))

  def block(blockHeight: Long): Block = Block(blockHeight)

  // use id > 0 to satisfy the where clause. No other reason.
  def lastBlockHeader: BlockHeader = lookupBlockHeader(" id > 0 ORDER BY height DESC LIMIT 1").getOrElse(genesisBlock())

  def blockHeaderOpt(height: Long): Option[BlockHeader] = {
    blockHeaderCache.get(height) match {
      case None => lookupBlockHeader(s"height = $height").map(foundHeader => {
        blockHeaderCache.put(height, foundHeader)
        foundHeader
      })

      case found@Some(_) => found
    }
  }


  def closeBlock(prevHeader: BlockHeader): BlockHeader = {

    val height = prevHeader.height + 1
    val block = Block(height)

    val hashPrevBlock = prevHeader.hash

    log.debug(s"Size of tx table is ${block.count}")

    db inTransaction {
      val txs = block.entries

      val newBlock = if (txs.nonEmpty) {
        val txIds: IndexedSeq[mutable.WrappedArray[Byte]] = txs.map(_.ledgerItem.txId: mutable.WrappedArray[Byte]).toIndexedSeq
        val mt: MerkleTree[mutable.WrappedArray[Byte]] = MerkleTree(txIds)
        mt.persist(MerklePersist.tableName(height))
        new BlockHeader(height, txs.size, hashPrevBlock, mt.root.array, new Date())
      } else new BlockHeader(height, 0, hashPrevBlock, Array(), new Date())

      val newRow = blockHeaderTable.insert(newBlock.asMap)
      log.debug(s"New Block header $newRow")
      BlockHeader(newRow)

    }
  }

  def addSignature(height: Long, signature: Signature, signersPublicKey: PublicKey, nodeId: String) = {
    blockHeaderOpt(height) match {
      case None => throw new IllegalArgumentException(s"No block exists of height $height")
      case Some(bh) =>
        if(!PublicKeyAccount(signersPublicKey).verify(signature, bh.hash)) {
          throw new IllegalArgumentException(s"The signature does not match the blockheader (height-$height)")
        }
        BlockSignatures(height).indexOfBlockSignature(nodeId) match {
          case None =>
            BlockSignatures(height).add(signature, signersPublicKey, nodeId)
          case Some(indx) =>
            log.warn(s"Already have sig from ${nodeId} at index $indx")
            BlockSignatures(height).signatures(indx + 1)(indx)
        }
    }
  }

  def indexOfBlockSignature(height: Long, nodeId: String): Option[Int] = BlockSignatures(height).indexOfBlockSignature(nodeId)

  def signatures(height: Long, maxToReturn: Int): Iterable[BlockSignature] = {
    BlockSignatures(height).signatures(maxToReturn)
  }

}