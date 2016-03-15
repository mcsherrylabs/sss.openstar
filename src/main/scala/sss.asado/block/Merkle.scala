package sss.asado.block


trait Merkle[A] {
  var parent: Option[Merkle[A]] = None
  def path: Seq[A]
}

case class RootMerkle[A](value: A) extends Merkle[A] {
  override def path: Seq[A] = {
    parent match {
      case None => Seq(value)
      case Some(m) => Seq(value) ++ m.path
    }
  }
}

class MerkleTree[A](val left: Merkle[A], val right: Merkle[A])(implicit f:(A,A)=>A) extends Merkle[A] {

  override def path: Seq[A] = {
    parent match {
      case None => Seq(root)
      case Some(m) => Seq(root) ++ m.path
    }
  }

  left.parent = Some(this)
  right.parent = Some(this)

  def contains(a: A): Boolean = (left, right) match {
    case (RootMerkle(aValue: A), RootMerkle(bValue: A)) => a == aValue || a == bValue
  }

  def +(m: MerkleTree[A]): MerkleTree[A] = new MerkleTree(this, m)
  def root: A  = (left, right) match {
    case (RootMerkle(aValue: A), RootMerkle(bValue: A)) => f(aValue, bValue)
    case (a : MerkleTree[A], b: MerkleTree[A]) => f(a.root , b.root)
  }
}

trait InvertedMerkleTree[A] {
  def apply(a: A): Option[Seq[A]]
  def contains(a: A): Boolean
  val root: MerkleTree[A]
}

object MerkleTree {

  def apply[A](a: A, b: A)(implicit f:(A,A)=>A): MerkleTree[A] = new MerkleTree(new RootMerkle[A](a), new RootMerkle[A](b))
  def apply[A](a: A)(implicit f:(A,A)=>A): MerkleTree[A] = apply(a,a)
}
