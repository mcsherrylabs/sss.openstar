package sss.asado.block

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
  }

  "A two element merkle " should " add the elements " in {

    val mt = MerkleTree[Int](10, 20)
    assert(mt.root === 30)
  }

  "Two merkle trees " should " add together " in {

    val mt = MerkleTree[Int](10, 20)
    val mt2 = MerkleTree[Int](30, 40)
    val mt3 = mt + mt2
    assert(mt.path === Seq(30, 100))
    assert(mt3.root === 10 + 20 + 30 + 40)
  }


  "3 deep merkle trees " should " have correct path " in {



    val in = (1 to 99)
    val trees = in.sliding(2,2).map { lst =>
      lst.toList match {
        case head :: Nil => MerkleTree[Int](head)
        case head :: tail :: Nil => MerkleTree[Int](head, tail)
        case x => throw new Exception(s"What? $x")
      }
    }

    def toTree[A](row: Iterator[Seq[MerkleTree[A]]]): Iterator[MerkleTree[A]] = {
      row map { pair =>
        if(pair.size == 1) pair(0) + pair(0)
        else pair(0) + pair(1)
      }
    }

    def newRow[A](lastRow: Iterator[MerkleTree[A]]): MerkleTree[A] = {
      println(lastRow.toList.size)
      val nextRow = toTree(lastRow.sliding(2, 2))
      if (nextRow.size == 1) nextRow.next()
      else newRow(nextRow)
    }

    newRow[Int](trees)
  }


}
