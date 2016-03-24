package sss.asado.block.serialize

import block.AckConfirmTx
import com.google.common.primitives.Longs
import ledger.TxIdLen
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object AckConfirmTxSerializer extends Serializer[AckConfirmTx]{

  override def toBytes(ct: AckConfirmTx): Array[Byte] = ct.txId ++ Longs.toByteArray(ct.height) ++ Longs.toByteArray(ct.id)

  override def fromBytes(b: Array[Byte]): AckConfirmTx = {
    val (txid, indexBytes) = b.splitAt(TxIdLen)
    val (heightBs, dbIdBs) = indexBytes.splitAt(8)
    val height = Longs.fromByteArray(heightBs)
    val id = Longs.fromByteArray(dbIdBs)
    AckConfirmTx(txid, height, id)
  }

}
