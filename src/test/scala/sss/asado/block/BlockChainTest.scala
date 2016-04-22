package sss.asado.block

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */
class BlockChainTest extends FlatSpec with Matchers {

  val merkleRoot= "12345678123456781234567812345678".getBytes
  val prevHash = "12345678123456781234567812345678".getBytes
  implicit val db = Db("DBStorageTest")
  val bc = new BlockChainImpl()
  val height = 1
  val numTxs = 0
  val time = new Date
  val newHeight = height + 1
  Block(height).truncate
  Block(newHeight).truncate


  "A block header in the chain " should " have it's hash in the following block header " in {

    val header1 = BlockHeader(1, 0 , prevHash, merkleRoot, new Date())
    val header2 = BlockHeader(1, 0, header1.hash, merkleRoot, new Date())

    assert(header2.hashPrevBlock === header1.hash)
  }

  it should " persist and retrieve consistently " in {

    val header1 = BlockHeader(height, numTxs, prevHash, merkleRoot, time)
    val retrieved = bc.blockHeaderTable.insert(header1.asMap)
    assert(BlockHeader(retrieved) === header1)
    assert(BlockHeader(retrieved).hashCode === header1.hashCode)
    assert(header1 !== retrieved)
  }

  private def matchFirstBlock(block: BlockHeader): Unit = {
    assert(block.height === height)
    assert(block.numTxs === numTxs)
    assert(block.time === time)
    assert(block.merkleRoot === merkleRoot)
    assert(block.hashPrevBlock === prevHash)
  }

  private def matchSecondBlock(block: BlockHeader, lastBlockHash: Array[Byte]): Unit = {
    assert(block.height === height + 1)
    assert(block.numTxs === 1)
    assert(new Date().getTime - block.time.getTime < 1000)
    //assert(block.merkleRoot !== merkleRoot)
    assert(block.hashPrevBlock === lastBlockHash)
  }

  it should " find the correct last block " in {
    matchFirstBlock(bc.lastBlockHeader)
  }


  it should " close a block correctly " in {

    val now = new Date()
    val lastBlock = bc.lastBlockHeader
    val txWriter = Block(newHeight)
    val stx = BlockTestSpec.createSignedTx(BlockTestSpec.createGenesis)
    txWriter.write(stx)
    bc.closeBlock(lastBlock)

    matchSecondBlock(bc.lastBlockHeader, lastBlock.hash)

  }

  it should " lookup up a block correctly " in {

    val firstBlock = bc.blockHeader(height)
    matchFirstBlock(firstBlock)
    matchSecondBlock(bc.blockHeader(height + 1), firstBlock.hash)

  }

  it should " create a genesis block " in {
    val firstBlock = bc.genesisBlock()
    assert(firstBlock.height === 0)
    assert(firstBlock.numTxs === 0)

  }

  it should " prevent a second genesis block " in {
    intercept[Exception]{bc.genesisBlock()}
  }


}
