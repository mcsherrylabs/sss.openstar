package sss.asado.block.serialize

import block.Leader
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object LeaderSerializer extends Serializer[Leader]{

  override def toBytes(l: Leader): Array[Byte] = l.nodeId.getBytes
  override def fromBytes(b: Array[Byte]): Leader = Leader(new String(b))

}
