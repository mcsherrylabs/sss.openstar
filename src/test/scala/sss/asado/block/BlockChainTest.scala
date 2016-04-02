package sss.asado.block

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.storage.{TxDBStorage, TxDBStorageTest}
import sss.db.Db

import scala.util.{Failure, Success}

/**
  * Created by alan on 2/15/16.
  */
class BlockChainTest extends FlatSpec with Matchers {

  val merkleRoot= "12345678123456781234567812345678".getBytes
  val prevHash = "12345678123456781234567812345678".getBytes
  implicit val db = Db("DBStorageTest")
  val bc = new BlockChain()
  val height = 1
  val numTxs = 0
  val time = new Date
  val newHeight = height + 1
  //bc.blockHeaderTable.insert(height, numTxs, prevHash, merkleRoot, time)

  "A block header in the chain " should " have it's hash in the following block header " in {

    val header1 = BlockHeader(1, 0 , prevHash, merkleRoot, new Date())
    val header2 = BlockHeader(1, 0, header1.hash, merkleRoot, new Date())

    assert(header2.hashPrevBlock === header1.hash)
  }

  it should " persist and retrieve consistently " in {

    val header1 = BlockHeader(height, numTxs, prevHash, merkleRoot, time)
    val retrieved = bc.blockHeaderTable.insert(header1.asMap)
    assert(BlockHeader(retrieved) === header1)

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
    bc.lastBlock match {
      case Success(block) => matchFirstBlock(block)
      case Failure(_) => fail("No last block?!")
    }
  }


  it should " close a block correctly " in {

    val now = new Date()
    val lastBlock = bc.lastBlock.get
    val txWriter = TxDBStorage(newHeight)
    val stx = TxDBStorageTest.createSignedTx(TxDBStorageTest.createGenesis)
    txWriter.write(stx.txId, stx)
    bc.closeBlock(lastBlock)

    bc.lastBlock match {
      case Success(block) => matchSecondBlock(block, lastBlock.hash)
      case Failure(_) => fail("No last block?!")
    }

  }

  it should " lookup up a block correctly " in {

    val firstBlock = bc.block(height).get
    matchFirstBlock(firstBlock)
    matchSecondBlock(bc.block(height + 1).get, firstBlock.hash)

  }

  it should " sign a block correctly " in {

    bc.sign(1, "")

  }

}
