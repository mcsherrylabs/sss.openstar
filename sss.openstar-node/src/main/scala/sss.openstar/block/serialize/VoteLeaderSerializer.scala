package sss.openstar.block.serialize

import java.nio.charset.StandardCharsets

import sss.openstar.block.VoteLeader
import sss.openstar.util.Serialize._

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object VoteLeaderSerializer extends Serializer[VoteLeader]{

  override def toBytes(l: VoteLeader): Array[Byte] =
    StringSerializer(l.nodeId) ++
      LongSerializer(l.height) ++
      LongSerializer(l.committedTxIndex)
        .toBytes

  override def fromBytes(b: Array[Byte]): VoteLeader =
    VoteLeader
    .tupled(b.extract(
      StringDeSerialize,
      LongDeSerialize,
      LongDeSerialize)
    )

}
