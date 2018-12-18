package sss.openstar.block.merkle

import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by alan on 2/15/16.
  */
class MerkleTest extends FlatSpec with Matchers {

  //implicit def hash(a:Array[Byte], b: Array[Byte]): Array[Byte] = FastCryptographicHash.hash(a ++ b)

  implicit def add(a:Int, b:Int): Int = a + b

  "A single element merkle " should " use itself as second element " in {
    val mt = MerkleTree[Int](10)
    assert(mt.root === 20)
    assert(mt.path(10) === Seq(10))
  }

  "A two element merkle " should " merge(hash) the elements " in {

    val mt = MerkleTree[Int](10, 20)
    assert(mt.root === 30)
    assert(mt.path(10) === Seq(20))
  }

  "A single element merkle seq " should " work " in {

    val mt = MerkleTree[Int](IndexedSeq(10))
    assert(mt.root === 20)
    assert(mt.path(10) === Seq(10))
  }

  "Two merkle trees " should " add together " in {

    val mt = MerkleTree[Int](10, 20)
    val mt2 = MerkleTree[Int](30, 40)
    val mt3 = mt + mt2
    assert(mt3.path(10) === Seq(20, 70))
    assert(mt3.root === 10 + 20 + 30 + 40)
    assert(MerkleTree.verify[Int](10, Seq(20, 70), mt3.root))
    assert(!MerkleTree.verify[Int](10, Seq(20, 40), mt3.root))
  }


  "Large merkle trees " should " have verifiable trees " in {

    val mt: MerkleTree[Int] = MerkleTree[Int]((0 to 99))

    (0 to 99) map { i =>
      val path = mt.path(i)
      //println(s"$i, $path, ${mt.root} " + MerkleTree.verify[Int](i, path, mt.root))
      assert(MerkleTree.verify[Int](i, path, mt.root))
    }

    val path = mt.path(4)
    assert(!MerkleTree.verify[Int](3, path, mt.root))

    intercept[NoSuchElementException](mt.path(101))
  }

}
