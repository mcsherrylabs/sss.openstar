package sss.asado.block.serialize

import java.nio.charset.StandardCharsets

import block.VoteLeader
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object VoteLeaderSerializer extends Serializer[VoteLeader]{

  override def toBytes(l: VoteLeader): Array[Byte] = l.nodeId.getBytes(StandardCharsets.UTF_8)
  override def fromBytes(b: Array[Byte]): VoteLeader = VoteLeader(new String(b, StandardCharsets.UTF_8))

}
