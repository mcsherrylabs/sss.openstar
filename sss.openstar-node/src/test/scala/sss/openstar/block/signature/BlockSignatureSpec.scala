package sss.openstar.block.signature

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.DummySeedBytes
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.db.Db

/**
  * Created by alan on 4/22/16.
  */
class BlockSignatureSpec  extends FlatSpec with Matchers {

  implicit val db: Db = Db()
  implicit val chainId: GlobalChainIdMask = 2.toByte

  "A Block Sig" should " be persisted " in {
    BlockSignatures.QuorumSigs(2).add(DummySeedBytes(50), DummySeedBytes(90), "someNodeId")
  }

  it should "prevent a second signature from the same node id " in {
    intercept[Exception] {BlockSignatures.QuorumSigs(2).add(DummySeedBytes(50), DummySeedBytes(90),"someNodeId")}
  }

  it should " not retrieve a non existent sig " in {
    assert(BlockSignatures.QuorumSigs(2).indexOfBlockSignature("NEVERADDED").isEmpty)
    assert(BlockSignatures.QuorumSigs(22).indexOfBlockSignature("NEVERADDED").isEmpty)
  }

  it should " re write a sig correctly " in {
    val sigAdded = BlockSignatures.QuorumSigs(3).add(DummySeedBytes(50), DummySeedBytes(90), "someNodeId")
    val sigRewritten = BlockSignatures.QuorumSigs(3).write(sigAdded)
    assert(sigAdded === sigRewritten)
  }

  it should " retrieve the correct index for signatures " in {
    (0 to 10) foreach  { i =>
      BlockSignatures.QuorumSigs(4).add(DummySeedBytes(50), DummySeedBytes(90), s"nodeId$i")
    }

    (0 to 10) foreach  { i =>
      assert(BlockSignatures.QuorumSigs(4).indexOfBlockSignature(s"nodeId$i").isDefined)
      assert(BlockSignatures.QuorumSigs(4).indexOfBlockSignature(s"nodeId$i").get  === i+1)
    }

  }

  it should " retrieve only the specified number of signatures but in order " in {
    (0 to 10) foreach  { i =>
      BlockSignatures.QuorumSigs(5).add(DummySeedBytes(50), DummySeedBytes(90), s"nodeId$i")
    }
    val returned = BlockSignatures.QuorumSigs(5).signatures(5)
    assert(returned.size === 5)
    for(i <- returned.indices) {assert(returned(i).index === i+1)}
  }
}
