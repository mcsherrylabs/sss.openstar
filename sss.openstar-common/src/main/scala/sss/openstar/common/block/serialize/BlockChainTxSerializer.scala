package sss.openstar.common.block.serialize

import sss.openstar.common.block._
import sss.openstar.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  */
object BlockChainTxSerializer extends Serializer[BlockChainTx] {

  override def toBytes(btx: BlockChainTx): Array[Byte] =
    LongSerializer(btx.height) ++
      btx.blockTx.toBytes

  override def fromBytes(b: Array[Byte]): BlockChainTx = {

    BlockChainTx.tupled(
      b.extract(LongDeSerialize, ByteArrayRawDeSerialize(_.toBlockTx))
    )

  }

}
