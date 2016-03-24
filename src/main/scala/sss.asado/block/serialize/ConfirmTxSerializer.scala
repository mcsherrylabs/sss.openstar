package sss.asado.block.serialize

import block.ConfirmTx
import com.google.common.primitives.Longs
import ledger._
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object ConfirmTxSerializer extends Serializer[ConfirmTx]{

  override def toBytes(ct: ConfirmTx): Array[Byte] = Longs.toByteArray(ct.height) ++ Longs.toByteArray(ct.id) ++ ct.stx.toBytes

  override def fromBytes(b: Array[Byte]): ConfirmTx = {
    val (heightBs, rest) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)
    val (dbIdBs, txBytes) = rest.splitAt(8)
    val id = Longs.fromByteArray(dbIdBs)
    ConfirmTx(txBytes.toSignedTx, height, id)
  }

}
