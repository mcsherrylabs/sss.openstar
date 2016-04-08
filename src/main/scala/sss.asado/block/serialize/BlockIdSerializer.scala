package sss.asado.block.serialize

import block.BlockId
import com.google.common.primitives.Longs
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object BlockIdSerializer extends Serializer[BlockId]{

  override def toBytes(blockId: BlockId): Array[Byte] = {
    Longs.toByteArray(blockId.blockHeight) ++
      Longs.toByteArray(blockId.numTxs)
  }

  override def fromBytes(b: Array[Byte]): BlockId = {
    val (heightBs, numTxsBs) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)
    val numTxs = Longs.fromByteArray(numTxsBs)

    BlockId(height, numTxs)
  }

}
