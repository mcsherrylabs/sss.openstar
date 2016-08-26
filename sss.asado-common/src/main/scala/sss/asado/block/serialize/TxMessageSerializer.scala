package sss.asado.block.serialize

import sss.asado.block._
import sss.asado.util.Serialize._


/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object TxMessageSerializer extends Serializer[TxMessage]{

  override def toBytes(txMessage: TxMessage): Array[Byte] = {
    (ByteSerializer(txMessage.msgType) ++
      ByteArraySerializer(txMessage.txId) ++
      StringSerializer(txMessage.msg)).toBytes
  }

  override def fromBytes(b: Array[Byte]): TxMessage = {
    val extracted = b.extract(ByteDeSerialize, ByteArrayDeSerialize, StringDeSerialize)
    TxMessage(extracted(0)[Byte],
      extracted(1)[Array[Byte]],
      extracted(2)[String])
  }

}
