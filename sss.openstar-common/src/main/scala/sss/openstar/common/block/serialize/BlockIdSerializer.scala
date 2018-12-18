package sss.openstar.common.block.serialize

import sss.openstar.common.block._
import sss.openstar.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  */
object BlockIdSerializer extends Serializer[BlockId] {

  override def toBytes(blockId: BlockId): Array[Byte] = {
    LongSerializer(blockId.blockHeight) ++
      LongSerializer(blockId.txIndex).toBytes
  }

  override def fromBytes(b: Array[Byte]): BlockId = {
    BlockId.tupled(b.extract(LongDeSerialize, LongDeSerialize))
  }

}
