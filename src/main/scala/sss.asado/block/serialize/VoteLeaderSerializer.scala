package sss.asado.block.serialize

import block.VoteLeader
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object VoteLeaderSerializer extends Serializer[VoteLeader]{

  override def toBytes(l: VoteLeader): Array[Byte] = l.nodeId.getBytes
  override def fromBytes(b: Array[Byte]): VoteLeader = VoteLeader(new String(b))

}
