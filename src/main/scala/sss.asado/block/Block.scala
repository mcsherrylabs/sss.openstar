package sss.asado.block

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/14/16.
  */
/*object Block {

  def apply(prevHeader: BlockHeader, txs: Seq[SignedTx]): Block = {
    val merkle = MerkleTree(txs)
    val header = BlockHeader(prevHeader.height + 1, prevHeader.hash, merkle, new Date)
    new Block(header, merkle)
  }
}

object MerkleTree {

  def apply(txs: Seq[SignedTx]): MerkleTree = new MerkleTree(txs.map(_.txId))

}

trait Merkle {
  def find(a: Array[Byte]): Boolean = ???
}

case class RegularMerkle(a: Array[Byte], b: Array[Byte]) extends Merkle {
  lazy val parent: Array[Byte] = FastCryptographicHash.hash(a ++ b)
}

trait MerkleRow
case class RootMerkle(root: Array[Byte]) extends MerkleRow
case class MerkleRow(row : Seq[RegularMerkle], height: Int) extends MerkleRow

class MerkleTree(val leaves: Seq[Array[Byte]])  {

  val height: Int = Math.floorMod(leaves.size, 2)

  def toRow(h: Int, r: Seq[Array[Byte]]): MerkleRow = {
    if(h == 0) RootMerkle(r(0))
    else MerkleRow(reduce(Seq.empty, r), h - 1)
  }

  @tailrec
  private def reduce(acc: Seq[Merkle], leaves: Seq[TxId]): Seq[Merkle] = {
    leaves match {
      case Nil => acc
      case txId :: Nil => reduce(acc :+ RegularMerkle(txId, txId), Nil)
      case txId :: rest => reduce(acc :+ RegularMerkle(txId, rest.head), rest.tail)
    }
  }

  val merkleRow: MerkleRow = toRow(height, leaves)

  def path(txId: TxId) = merkleRow match {
    case RootMerkle(hash) if(hash == txId) => Seq(txId)
    case MerkleRow(row, h) => row.find(m => m.a == txId || m.b == txId)
  }
}


case class BlockHeader(height: Int, hashPrevBlock: Array[Byte], merkleRoot: MerkleTree, time: Date) {
  lazy val hash:Array[Byte] = ???
}

case class Block(header: BlockHeader, merkle: MerkleTree)*/
