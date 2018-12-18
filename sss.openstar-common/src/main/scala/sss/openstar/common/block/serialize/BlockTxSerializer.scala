package sss.openstar.common.block.serialize

import sss.openstar.common.block._
import sss.openstar.ledger._
import sss.openstar.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  */
object BlockTxSerializer extends Serializer[BlockTx] {

  override def toBytes(btx: BlockTx): Array[Byte] =
    LongSerializer(btx.index) ++
      btx.ledgerItem.toBytes

  override def fromBytes(b: Array[Byte]): BlockTx = {
    BlockTx.tupled(
      b.extract(LongDeSerialize, ByteArrayRawDeSerialize(_.toLedgerItem)))
  }

}
