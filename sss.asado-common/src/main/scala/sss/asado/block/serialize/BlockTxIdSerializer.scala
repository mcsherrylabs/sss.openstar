package sss.asado.block.serialize

import sss.asado.block._
import sss.asado.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  */
object BlockTxIdSerializer extends Serializer[BlockTxId] {

  override def toBytes(blockTxId: BlockTxId): Array[Byte] = {
    ByteArraySerializer(blockTxId.txId) ++
      LongSerializer(blockTxId.index).toBytes
  }

  override def fromBytes(b: Array[Byte]): BlockTxId = {

    BlockTxId.tupled(
      b.extract(ByteArrayDeSerialize, LongDeSerialize)
    )
  }

}
