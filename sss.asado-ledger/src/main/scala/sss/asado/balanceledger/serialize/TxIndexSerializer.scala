package sss.asado.balanceledger.serialize

import sss.asado.balanceledger.TxIndex
import com.google.common.primitives.Ints

import sss.asado.ledger._
import sss.asado.util.Serialize.Serializer
/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object TxIndexSerializer extends Serializer[TxIndex]{

  override def toBytes(t: TxIndex): Array[Byte] = t.txId ++ Ints.toByteArray(t.index)

  override def fromBytes(b: Array[Byte]): TxIndex = {
    val (id, indexBytes) = b.splitAt(TxIdLen)
    val index = Ints.fromByteArray(indexBytes)
    TxIndex(id, index)
  }

}
