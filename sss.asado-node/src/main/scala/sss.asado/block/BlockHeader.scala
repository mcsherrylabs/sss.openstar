package sss.asado.block

import java.util
import java.util.Date

import com.google.common.primitives.Longs
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.util.hash.SecureCryptographicHash
import sss.db.Row

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/14/16.
  */

case class BlockHeader(
  height: Long,
  numTxs: Int,
  hashPrevBlock: Array[Byte],
  merkleRoot: Array[Byte],
  time: Date) extends ByteArrayComparisonOps {

  lazy val asMap: Map[String, Any] = Map(
    BlockHeader.heightStr -> height,
    BlockHeader.numTxsStr -> numTxs,
    BlockHeader.prevBlockStr -> hashPrevBlock,
    BlockHeader.merkleRootStr -> merkleRoot,
    BlockHeader.mineTimeStr -> time)

  lazy val hash: Array[Byte] = SecureCryptographicHash.hash(Longs.toByteArray(height) ++ hashPrevBlock ++ merkleRoot)

  override def equals(obj: scala.Any): Boolean = obj match {
    case header: BlockHeader =>
      header.numTxs == numTxs &&
      header.height == height &&
      header.hashPrevBlock.isSame(hashPrevBlock) &&
      header.merkleRoot.isSame(merkleRoot) &&
      header.time == time
    case _ => false
  }

  override def hashCode(): Int = {
    (17 + Longs.hashCode(height)) *
      (numTxs + util.Arrays.hashCode(hashPrevBlock) +
        util.Arrays.hashCode(merkleRoot) +
        time.hashCode)
  }
}

object BlockHeader {

  val heightStr = "height"
  val numTxsStr = "num_txs"
  val prevBlockStr = "prev_block"
  val merkleRootStr = "merkle_root"
  val mineTimeStr = "mine_dt"

  def apply(row: Row): BlockHeader = {


    BlockHeader(
      row[Long](heightStr),
      row[Int](numTxsStr),
      row[Array[Byte]](prevBlockStr),
      row[Array[Byte]](merkleRootStr),
      new Date(row[Long](mineTimeStr)))
  }
}


