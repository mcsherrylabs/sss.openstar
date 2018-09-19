package sss.asado.common.block.serialize

import sss.asado.common.block._
import sss.asado.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved.
  * mcsherrylabs on 3/3/16.
  */
object TxMessageSerializer extends Serializer[TxMessage] {

  override def toBytes(txMessage: TxMessage): Array[Byte] = {
    (ByteSerializer(txMessage.msgType) ++
      ByteArraySerializer(txMessage.txId) ++
      StringSerializer(txMessage.msg)).toBytes
  }

  override def fromBytes(b: Array[Byte]): TxMessage = {

    TxMessage.tupled(
      b.extract(
        ByteDeSerialize,
        ByteArrayDeSerialize,
        StringDeSerialize
      )
    )
  }

}
