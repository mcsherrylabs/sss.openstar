package sss.asado.block.serialize

import block.{DistributeClose, TxMessage}
import com.google.common.primitives.Ints
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.util.Serialize.Serializer

import scala.annotation.tailrec

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object TxMessageSerializer extends Serializer[TxMessage]{

  override def toBytes(txMessage: TxMessage): Array[Byte] = {
    Ints.toByteArray(txMessage.txId.length) ++ txMessage.txId ++ txMessage.msg.getBytes
  }

  override def fromBytes(b: Array[Byte]): TxMessage = {
    val (lenTxIdBytes, rest ) = b.splitAt(4)
    val lenTx = Ints.fromByteArray(lenTxIdBytes)
    val (txId, msgBytes) = rest.splitAt(lenTx)
    TxMessage(txId, new String(msgBytes))
  }

}
