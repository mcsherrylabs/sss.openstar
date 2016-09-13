package sss.asado.block.serialize

import com.google.common.primitives.Longs
import sss.asado.block._
import sss.asado.ledger._
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object BlockTxSerializer extends Serializer[BlockTx]{

  override def toBytes(btx: BlockTx): Array[Byte] = Longs.toByteArray(btx.index) ++ btx.ledgerItem.toBytes

  override def fromBytes(b: Array[Byte]): BlockTx = {
    val (indexBs, rest) = b.splitAt(8)
    val index = Longs.fromByteArray(indexBs)
    BlockTx(index, rest.toLedgerItem)
  }

}
