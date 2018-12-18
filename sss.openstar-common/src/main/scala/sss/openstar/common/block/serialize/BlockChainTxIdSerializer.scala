package sss.openstar.common.block.serialize

import sss.openstar.common.block._
import sss.openstar.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  */
object BlockChainTxIdSerializer extends Serializer[BlockChainTxId] {

  override def toBytes(blockChainTxId: BlockChainTxId): Array[Byte] = {
    LongSerializer(blockChainTxId.height) ++
      blockChainTxId.blockTxId.toBytes

  }

  override def fromBytes(b: Array[Byte]): BlockChainTxId = {
    BlockChainTxId.tupled(
      b.extract(LongDeSerialize, ByteArrayRawDeSerialize(_.toBlockIdTx))
    )
  }

}
