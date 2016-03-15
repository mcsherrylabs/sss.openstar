package sss.asado.block

/**
  * Created by alan on 2/15/16.
  */
//class MerkleTest extends FlatSpec with Matchers {
object Main {
  //implicit def hash(a:Array[Byte], b: Array[Byte]): Array[Byte] = FastCryptographicHash.hash(a ++ b)

  implicit def add(a:Int, b:Int): Int = a + b

  def main(args: Array[String]) {

    val in = (1 to 99)
    val trees = in.grouped(2).map { lst =>
      lst.toList match {
        case head :: Nil => MerkleTree[Int](head)
        case head :: tail :: Nil => MerkleTree[Int](head, tail)
        case x => throw new Exception(s"What? $x")
      }
    }



    val list = trees.toList

    def toMerkleTree[A](seq: IndexedSeq[A])(implicit f:(A,A)=>A): InvertedMerkleTree[A] = {

      def toTree[A](row: IndexedSeq[IndexedSeq[MerkleTree[A]]]): IndexedSeq[MerkleTree[A]] = {
        row map { pair =>
          if(pair.size == 1) pair(0) + pair(0)
          else pair(0) + pair(1)
        }
      }

      def newRow[A](lastRow: IndexedSeq[MerkleTree[A]]): MerkleTree[A] = {

        val nextRow = toTree[A](lastRow.grouped(2).toIndexedSeq)
        if (nextRow.size == 1) nextRow(0)
        else newRow(nextRow)
      }

      val trees = seq.grouped(2).map { lst =>
        lst match {
          case head +: Seq() => MerkleTree[A](head)
          case head +: tail +: Seq() => MerkleTree[A](head, tail)
          case x => throw new Exception(s"What? $x")
        }
      }.toList

      val tmpRoot = newRow[A](trees.toIndexedSeq)

      new InvertedMerkleTree[A] {
        override def apply(a: A): Option[Seq[A]] = trees.find(_.contains(a)).map(_.path)

        override def contains(a: A): Boolean = trees.find(_.contains(a)).isDefined

        override val root: MerkleTree[A] = tmpRoot
      }
    }

    val d = toMerkleTree((1 to 99))

    println("ROOT " + d.root.path)

    (0 to 98) foreach { i =>
      println(s"$i ${d(i)}")
    }

  }


}
