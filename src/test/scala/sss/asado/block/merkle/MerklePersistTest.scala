package sss.asado.block.merkle

import java.util.Date

import org.scalatest.{FlatSpec, Matchers}
import sss.asado.util.SeedBytes
import sss.db.Db

import scala.collection.mutable

/**
  * Created by alan on 2/15/16.
  */
class MerklePersistTest extends FlatSpec with Matchers {

//  implicit def hash(a:mutable.WrappedArray[Byte], b: mutable.WrappedArray[Byte]): mutable.WrappedArray[Byte] =
//    FastCryptographicHash.hash(a.array) ++ FastCryptographicHash.hash(b.array)

  implicit val db: Db = Db()

  import MerklePersist._

  lazy val leafs: IndexedSeq[mutable.WrappedArray[Byte]] = (0 to 99) map(i => {
    val k : mutable.WrappedArray[Byte] = SeedBytes(32)
    k})

  lazy val mt: MerkleTree[mutable.WrappedArray[Byte]] = MerkleTree[mutable.WrappedArray[Byte]](leafs)
  lazy val root = mt.root

  "Merkle trees " should " be writable to disk " in {

    val start = new Date()
    mt.persist("test1")

    val dur = (new Date().getTime) - start.getTime
    println(s"${leafs.size} took $dur ms to persist => ${dur/leafs.size} ms / 32 byte element")

  }


  "A merkle path " should " be retrievable from disk " in {

    val start = new Date()

    leafs foreach { leaf =>
      val p = MerklePersist.path("test1", leaf.array)
      val ary: Seq[mutable.WrappedArray[Byte]] = p.get.map( bs => bs : mutable.WrappedArray[Byte])
      MerkleTree.verify[mutable.WrappedArray[Byte]](leaf, ary, root)
    }

    val dur = (new Date().getTime) - start.getTime
    println(s"${leafs.size} took $dur ms => ${dur/leafs.size} ms / retrieve and verify")
  }

}
