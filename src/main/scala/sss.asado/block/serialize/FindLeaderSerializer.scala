package sss.asado.block.serialize

import block.FindLeader
import com.google.common.primitives.{Ints, Longs}
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object FindLeaderSerializer extends Serializer[FindLeader]{

  override def toBytes(fl: FindLeader): Array[Byte] = Longs.toByteArray(fl.height) ++ Ints.toByteArray(fl.signatureIndex) ++ fl.nodeId.getBytes

  override def fromBytes(b: Array[Byte]): FindLeader = {
    val (heightBs, rest) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)
    val (sigIndxBytes, nodeBs) = rest.splitAt(4)
    val sigIndx = Ints.fromByteArray(sigIndxBytes)
    FindLeader(height, sigIndx, new String(nodeBs))
  }

}
