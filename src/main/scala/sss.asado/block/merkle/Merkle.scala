package sss.asado.block.merkle

import scala.annotation.tailrec


trait MerkleTree[A] {
  implicit def hash:(A,A)=>A

  val leafs: Map[A, MerkleTree[A]]
  def path(a: A): Seq[A]
  val root: A
  def +(mt: MerkleTree[A]): MerkleTree[A] = MerkleTreeBranch(this, mt)

  protected[block] var parent: Option[MerkleTreeBranch[A]] = None

  protected def restPath: Seq[A] = {
    parent match {
      case None => Seq[A]()
      case Some(p) => p.sibling(this)
    }
  }
}

object MerkleTree {


  @tailrec
  final def verify[A](a: A, path: Seq[A], root: A)(implicit f:(A,A)=>A): Boolean = {
    path match {
      case Seq() => a == root
      case head +: tail => verify(f(a, head), tail, root)
    }
  }

  def apply[A](seq: IndexedSeq[A])(implicit f: (A, A) => A): MerkleTree[A] = {

    def reduce(row: IndexedSeq[IndexedSeq[MerkleTree[A]]]): IndexedSeq[MerkleTree[A]] = {
      row map { pair =>
        if (pair.size == 1) pair(0) + pair(0)
        else pair(0) + pair(1)
      }
    }

    @tailrec
    def build(lastRow: IndexedSeq[MerkleTree[A]]): MerkleTree[A] = {
      if (lastRow.size == 1) lastRow(0)
      else build(reduce(lastRow.grouped(2).toIndexedSeq))
    }

    val trees = seq.grouped(2).map { lst =>
      lst match {
        case head +: Seq() => MerkleTree[A](head)
        case head +: tail +: Seq() => MerkleTree[A](head, tail)
        case x => throw new Exception(s"What? $x")
      }
    }

    build(trees.toIndexedSeq)

  }

  def apply[A](a: A)(implicit f: (A, A) => A): MerkleTree[A] = apply(a,a)

  def apply[A](a: A, b: A)(implicit f: (A, A) => A): MerkleTree[A] = new MerkleTreeLeaf(a, b)
}


case class MerkleTreeBranch[A](val left: MerkleTree[A], val right: MerkleTree[A])(implicit f:(A,A)=>A) extends MerkleTree[A] {

  override def hash = implicitly

  left.parent = Some(this)
  right.parent = Some(this)

  override val leafs = left.leafs ++ right.leafs

  def sibling(mt: MerkleTree[A]): Seq[A] = mt match {
    case `left` => right.root +: restPath
    case `right` => left.root +: restPath

  }
  override def path(a: A): Seq[A] = leafs(a).path(a)

  override val root: A = hash(left.root, right.root)
}


case class MerkleTreeLeaf[A](val left: A, val right: A)(implicit f: (A,A) => A) extends MerkleTree[A] {

  override def hash = implicitly

  override val leafs = Map(left -> this, right -> this)

  override def path(a: A): Seq[A] = a match {
    case `left` => right +: restPath
    case `right` => left +: restPath
  }

  override val root: A = hash(left, right)
}

