package sss.asado.block.signature

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.SeedBytes
import sss.db.Db

/**
  * Created by alan on 4/22/16.
  */
class BlockSignatureSpec  extends FlatSpec with Matchers {

  implicit val db: Db = Db()

  "A Block Sig" should " be persisted " in {
    BlockSignatures(2).add(SeedBytes(50), SeedBytes(90), "someNodeId")
  }

  it should "prevent a second signature from the same node id " in {
    intercept[Exception] {BlockSignatures(2).add(SeedBytes(50), SeedBytes(90),"someNodeId")}
  }

  it should " not retrieve a non existent sig " in {
    assert(BlockSignatures(2).indexOfBlockSignature("NEVERADDED").isEmpty)
    assert(BlockSignatures(22).indexOfBlockSignature("NEVERADDED").isEmpty)
  }

  it should " retrieve the correct index for signatures " in {
    (0 to 10) foreach  { i =>
      BlockSignatures(4).add(SeedBytes(50), SeedBytes(90), s"nodeId$i")
    }

    (0 to 10) foreach  { i =>
      assert(BlockSignatures(4).indexOfBlockSignature(s"nodeId$i").isDefined)
      assert(BlockSignatures(4).indexOfBlockSignature(s"nodeId$i").get  === i+1)
    }


  }
}
