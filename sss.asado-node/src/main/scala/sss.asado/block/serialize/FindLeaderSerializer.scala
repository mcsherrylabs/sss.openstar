package sss.asado.block.serialize

import sss.asado.block.FindLeader

import sss.asado.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object FindLeaderSerializer extends Serializer[FindLeader]{

  override def toBytes(fl: FindLeader): Array[Byte] =
    (LongSerializer(fl.height) ++
      LongSerializer(fl.recordedTxIndex) ++
      LongSerializer(fl.committedTxIndex) ++
      IntSerializer(fl.signatureIndex) ++
      StringSerializer(fl.nodeId)).toBytes

  override def fromBytes(b: Array[Byte]): FindLeader = {
    FindLeader.tupled(
      b.extract(LongDeSerialize, LongDeSerialize, LongDeSerialize, IntDeSerialize, StringDeSerialize)
    )
  }

}
