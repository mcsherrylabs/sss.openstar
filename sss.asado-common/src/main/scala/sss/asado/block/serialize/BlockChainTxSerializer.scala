package sss.asado.block.serialize

import sss.asado.block._
import com.google.common.primitives.Longs
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object BlockChainTxSerializer extends Serializer[BlockChainTx]{

  override def toBytes(btx: BlockChainTx): Array[Byte] = Longs.toByteArray(btx.height) ++ btx.blockTx.toBytes

  override def fromBytes(b: Array[Byte]): BlockChainTx = {
    val (heightBs, rest) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)
    BlockChainTx(height, rest.toBlockTx)
  }

}
