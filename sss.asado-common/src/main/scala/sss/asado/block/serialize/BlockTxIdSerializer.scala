package sss.asado.block.serialize

import com.google.common.primitives.Longs
import sss.asado.block._
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object BlockTxIdSerializer extends Serializer[BlockTxId]{

  override def toBytes(blockTxId: BlockTxId): Array[Byte] = {
    Longs.toByteArray(blockTxId.index) ++
      blockTxId.txId
  }

  override def fromBytes(b: Array[Byte]): BlockTxId = {
    val (heightBs, rest) = b.splitAt(8)
    val index = Longs.fromByteArray(heightBs)

    BlockTxId(rest, index)
  }

}
