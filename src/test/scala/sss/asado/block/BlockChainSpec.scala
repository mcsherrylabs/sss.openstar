package sss.asado.block

import java.util.Date

import block.{BlockChainTxId, BlockTxId}
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.account.ClientKey
import sss.db.Db

/**
  * Created by alan on 2/15/16.
  */
class BlockChainSpec extends FlatSpec with Matchers {

  val merkleRoot= "12345678123456781234567812345678".getBytes
  val prevHash = "12345678123456781234567812345678".getBytes

  implicit val db = Db()
  val bc = new BlockChainImpl()

  var genBlk: BlockHeader = _



  "A block header in the chain " should " have it's hash in the following block header " in {

    val header1 = BlockHeader(1, 0 , prevHash, merkleRoot, new Date())
    val header2 = BlockHeader(1, 0, header1.hash, merkleRoot, new Date())

    assert(header2.hashPrevBlock === header1.hash)
  }


  "A block chain " should " create a genesis block " in {
    genBlk = bc.genesisBlock()
    assert(genBlk.height === 1)
    assert(genBlk.numTxs === 0)
  }

  it should " be able to sign a block header " in {

    val someNodeId = "whoareyou"
    val ck = ClientKey()
    val signed = ck.sign(genBlk.hash)
    bc.addSignature(1, signed, ClientKey().publicKey, someNodeId)
    assert(bc.indexOfBlockSignature(1, someNodeId).isDefined)
    assert(bc.indexOfBlockSignature(1, someNodeId).get == 1)

  }


  it should " refuse a signature for the wrong block " in {

    val someNodeId = "whoareyou"

    val header2 = BlockHeader(1, 0, genBlk.hash, merkleRoot, new Date())

    val ck = ClientKey()
    val signed = ck.sign(genBlk.hash)

    intercept[Exception] {
      bc.addSignature(1, signed, ck.publicKey, someNodeId)
    }

    // check non existant block...
    intercept[Exception] {
      bc.addSignature(Long.MaxValue, signed, ck.publicKey, someNodeId)
    }

  }

  it should " not find signatures that haven't occurred " in {
    val someNodeId = "totallunknown"
    assert(bc.indexOfBlockSignature(1, someNodeId).isEmpty)
  }

  it should " not find signatures that for another block " in {
    val someNodeId = "whoareyou"
    assert(bc.indexOfBlockSignature(99, someNodeId).isEmpty)
  }

  it should " find the correct last block " in {
    assert(bc.lastBlockHeader === genBlk)
  }


  private def matchSecondBlock(block: BlockHeader, lastBlockHash: Array[Byte]): Unit = {
    assert(block.height === 2)
    //assert(block.numTxs === 1)
    assert(new Date().getTime - block.time.getTime < 1000)
    //assert(block.merkleRoot !== merkleRoot)
    assert(block.hashPrevBlock === lastBlockHash)
  }
  it should " close a block correctly " in {

    val now = new Date()
    val lastBlock = bc.lastBlockHeader

    val newHeader = bc.closeBlock(lastBlock)

    matchSecondBlock(bc.lastBlockHeader, lastBlock.hash)

    bc.blockHeader(newHeader.height).hashCode() should be (newHeader.hashCode())
    assert(bc.lastBlockHeader === newHeader)
    assert(lastBlock !== newHeader)
  }

  it should " lookup up a block correctly " in {

    val firstBlock = bc.blockHeader(1)
    assert(firstBlock === genBlk)
    matchSecondBlock(bc.blockHeader(2), firstBlock.hash)

  }

  it should " prevent a second genesis block " in {
    intercept[Exception]{bc.genesisBlock()}
  }

  it should " get the unconfirmed " in {
    val height = bc.lastBlockHeader.height
    val num = Block(height).entries.size
    assert(bc.getUnconfirmed(height, 999).size === num)
  }

  it should " successfully confirm " in {
    val height = bc.lastBlockHeader.height
    var index = 0
    val size = Block(height).entries.size

    Block(height).entries foreach { entry =>
      bc.confirm(BlockChainTxId(height, BlockTxId(entry.signedTx.txId, index)))
      index += 1
    }
    assert(bc.getUnconfirmed(height, 1).size === 0)
    assert(bc.getUnconfirmed(height, 2).size === size)
    index = 0
    Block(height).entries foreach { entry =>
      bc.confirm(BlockChainTxId(height, BlockTxId(entry.signedTx.txId, index)))
      index += 1
    }
    assert(bc.getUnconfirmed(height, 2).size === 0)
  }


}
