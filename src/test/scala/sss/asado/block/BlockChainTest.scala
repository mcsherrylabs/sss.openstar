package sss.asado.block

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.storage.{TxDBStorage, TxDBStorageTest}
import sss.db.Db

import scala.util.Success

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

  it should " find the correct last block " in {
    bc.lastBlock match {
      case Success(block) => {
        assert(block.height === height)
        assert(block.numTxs === numTxs)
        assert(block.time === time)
        assert(block.merkleRoot === merkleRoot)
        assert(block.hashPrevBlock === prevHash)
      }
    }
  }

  it should " create the next blocks tables " in {

    bc.createBlock(newHeight)
    assert(db.table(bc.blockTableNamePrefix + newHeight).count == 0)

  }

  it should " close a block correctly " in {

    val txWriter = new TxDBStorage(bc.blockTableNamePrefix + newHeight)
    val stx = TxDBStorageTest.createSignedTx(TxDBStorageTest.createGenesis)
    txWriter.write(stx.txId, stx)
    bc.closeBlock

  }
}
