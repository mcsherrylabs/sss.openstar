package sss.asado.block.serialize


import com.google.common.primitives.Longs
import sss.asado.block._
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object BlockChainTxIdSerializer extends Serializer[BlockChainTxId]{

  override def toBytes(blockChainTxId: BlockChainTxId): Array[Byte] = {
    Longs.toByteArray(blockChainTxId.height) ++
      blockChainTxId.blockTxId.toBytes
  }

  override def fromBytes(b: Array[Byte]): BlockChainTxId = {
    val (heightBs, rest) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)

    BlockChainTxId(height, rest.toBlockIdTx)
  }

}
