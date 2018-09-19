package sss.asado.block.serialize

import sss.asado.block.{Synchronized}
import sss.asado.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object SynchronizedSerializer extends Serializer[Synchronized]{

  override def toBytes(sync: Synchronized): Array[Byte] =

    ByteSerializer(sync.chainIdMask) ++
      LongSerializer(sync.height) ++
      LongSerializer(sync.index)
        .toBytes


  override def fromBytes(b: Array[Byte]): Synchronized = {

    Synchronized.tupled(
      b.extract(
        ByteDeSerialize,
        LongDeSerialize,
        LongDeSerialize)
    )
  }

}
