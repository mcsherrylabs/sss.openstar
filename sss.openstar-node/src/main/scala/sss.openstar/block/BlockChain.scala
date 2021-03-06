package sss.openstar.block

import java.nio.charset.StandardCharsets
import java.util.Date

import scorex.crypto.signatures.SigningFunctions.{PublicKey, Signature}
import sss.ancillary.Logging

import sss.openstar.block.merkle.MerklePersist._
import sss.openstar.block.merkle.{MerklePersist, MerkleTree}
import sss.openstar.block.signature.BlockSignatures
import sss.openstar.block.signature.BlockSignatures.BlockSignature
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.common.block._

import sss.openstar.util.StringCheck.SimpleTag
import sss.db._
import sss.openstar.account.{NodeIdentity, PublicKeyAccount}

import scala.collection.mutable
import scala.util.Try


trait BlockChainGenesis {
  def genesisBlock(prevHash: String = "GENESIS".padTo(32, "8").toString, merkleRoot: String = "GENESIS".padTo(32, "8").toString): BlockHeader
}

trait BlockChainSignaturesAccessor {

  this: BlockChainImpl =>

  def quorumSigs(height:Long): BlockChainSignatures =
    new BlockChainSigsImpl(BlockSignatures.QuorumSigs(height), blockHeaderOpt(height))

  def nonQuorumSigs(height:Long): BlockChainSignatures =
    new BlockChainSigsImpl(BlockSignatures.NonQuorumSigs(height), blockHeaderOpt(height))

}

trait BlockChainSignatures {
  def addSignature(signature: Signature, signersPublicKey: PublicKey, nodeId: String): Try[BlockSignature]
  def indexOfBlockSignature(nodeId: String): Option[Int]
  def signatures(maxToReturn: Int): Iterable[BlockSignature]
  def sign(nodeIdentity: NodeIdentity, hash: Array[Byte]): BlockSignature
}

trait BlockChain {

  def getLatestRecordedBlockId(): BlockId = {
    val blockHeader = lastBlockHeader
    val biggestRecordedTxIndex =
      block(blockHeader.height + 1).getLastRecorded
    BlockId(blockHeader.height + 1, biggestRecordedTxIndex)
  }

  def getLatestCommittedBlockId(): BlockId = {
    val blockHeader = lastBlockHeader
    val biggestCommittedTxIndex =
      block(blockHeader.height + 1).getLastCommitted
    BlockId(blockHeader.height + 1, biggestCommittedTxIndex)
  }

  implicit val chainId: GlobalChainIdMask
  def block(blockHeight: Long): Block
  def lastBlockHeader: BlockHeader
  def blockHeaderOpt(height: Long): Option[BlockHeader]
  final def blockHeader(height: Long): BlockHeader = blockHeaderOpt(height).get
  def closeBlock(blockId: BlockId): Try[BlockHeader]
  def closeBlock(prevHeader: BlockHeader): Try[BlockHeader]
}

object BlockChainImpl {
  //private lazy val blockHeaderCache = new SynchronizedLruMap[Long, BlockHeader](100)
}

class BlockChainImpl(implicit val db: Db, val chainId: GlobalChainIdMask) extends BlockChain
  with BlockChainSignaturesAccessor
  with BlockChainGenesis
  with Logging {

  private val tag: SimpleTag = chainId
  private val tableName = s"blockchain_$tag"

  private val sql =
    s"""CREATE TABLE IF NOT EXISTS $tableName
      |(id BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
      |height BIGINT NOT NULL,
      |num_txs INT NOT NULL,
      |prev_block BLOB NOT NULL,
      |merkle_root BLOB NOT NULL,
      |mine_dt BIGINT NOT NULL,
      |PRIMARY KEY (height));""".stripMargin

  private lazy val blockHeaderTable = {
    db.executeSql(sql)
    db.table(tableName)
  }

  private def lookupBlockHeader(sql: Where): Option[BlockHeader] = {
    blockHeaderTable.find((sql)) map (BlockHeader(_))
  }

  //NB this creates the genises block if it doesn't exist.
  lastBlockHeader

  def genesisBlock(prevHash: String = "GENESIS".padTo(32, "8").toString, merkleRoot: String = "GENESIS".padTo(32, "8").toString): BlockHeader = {
    require(blockHeaderTable.count == 0)
    val genesisHeader = BlockHeader(1, 0, prevHash.substring(0, 32)
      .getBytes(StandardCharsets.UTF_8),
      merkleRoot.substring(0, 32).getBytes(StandardCharsets.UTF_8),
      new Date())

    BlockHeader(blockHeaderTable.insert(genesisHeader.asMap))
  }

  def block(blockHeight: Long): Block = Block(blockHeight)

  // use id > 0 to satisfy the where clause. No other reason. <-- FIXME
  def lastBlockHeader: BlockHeader =
    lookupBlockHeader(OrderDesc("height")
      .limit(1))
      .getOrElse(genesisBlock())

  def blockHeaderOpt(height: Long): Option[BlockHeader] = {
      lookupBlockHeader(where("height" -> height))
  }

  override def closeBlock(blockId: BlockId): Try[BlockHeader] = Try {

    val blk = block(blockId.blockHeight)
    val lst = lastBlockHeader
    assert(blk.getLastCommitted == blockId.txIndex, s"Expected number of Txs is ${blockId.txIndex} actual is ${blk.getLastCommitted}")
    assert(lst.height + 1 == blockId.blockHeight, s"Trying to close ${blockId.blockHeight} but last closed header is ${lst.height}")
    log.info("Closeblock ok {}, flatMap", blockId)
    lst
  } flatMap (closeBlock)

  override def closeBlock(prevHeader: BlockHeader): Try[BlockHeader] = Try {

    val height = prevHeader.height + 1
    val block = Block(height)

    val hashPrevBlock = prevHeader.hash

    db inTransaction {
      val txs = block.entries

      log.info("Num entries in close block now {}", txs.size)

      val newBlock = if (txs.nonEmpty) {
        val txIds: IndexedSeq[mutable.WrappedArray[Byte]] = txs.map(_.ledgerItem.txId: mutable.WrappedArray[Byte]).toIndexedSeq
        val mt: MerkleTree[mutable.WrappedArray[Byte]] = MerkleTree(txIds)
        mt.persist(MerklePersist.tableName(height))
        new BlockHeader(height, txs.size, hashPrevBlock, mt.root.array, new Date())
      } else new BlockHeader(height, 0, hashPrevBlock, Array(), new Date())

      val newRow = blockHeaderTable.insert(newBlock.asMap)
      val result = BlockHeader(newRow)
      log.debug(s"New BlockHeader $result")
      result


    }
  }

}

class BlockChainSigsImpl(impl: BlockSignatures, blockHeaderOpt: => Option[BlockHeader])
  extends Logging
    with BlockChainSignatures {

  def sign(nodeIdentity: NodeIdentity, hash: Array[Byte]): BlockSignature = {
    impl.add(
      nodeIdentity.sign(hash),
      nodeIdentity.publicKey,
      nodeIdentity.id)
  }

  def addSignature(signature: Signature, signersPublicKey: PublicKey, nodeId: String): Try[BlockSignature] = Try {
    blockHeaderOpt match {
      case None => throw new IllegalArgumentException(s"No block exists of height ${impl.height}")
      case Some(bh) =>
        if (!PublicKeyAccount(signersPublicKey).verify(signature, bh.hash)) {
          throw new IllegalArgumentException(s"The signature does not match the blockheader (height=${impl.height})")
        }
        impl.indexOfBlockSignature(nodeId) match {
          case None =>
            impl.add(signature, signersPublicKey, nodeId)
          case Some(indx) =>
            log.warn(s"Already have sig from ${nodeId} at index $indx")
            impl.signatures(indx)(indx - 1)
        }
    }
  }

  def indexOfBlockSignature(nodeId: String): Option[Int] = impl.indexOfBlockSignature(nodeId)

  def signatures(maxToReturn: Int): Iterable[BlockSignature] = {
    impl.signatures(maxToReturn)
  }
}
