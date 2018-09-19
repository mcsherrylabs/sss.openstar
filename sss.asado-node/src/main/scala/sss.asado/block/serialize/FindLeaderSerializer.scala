package sss.asado.block.serialize

import java.nio.charset.StandardCharsets

import sss.asado.block.FindLeader
import com.google.common.primitives.{Ints, Longs}
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object FindLeaderSerializer extends Serializer[FindLeader]{

  override def toBytes(fl: FindLeader): Array[Byte] =
    Longs.toByteArray(fl.height) ++
      Longs.toByteArray(fl.commitedTxIndex) ++
      Ints.toByteArray(fl.signatureIndex) ++
      fl.nodeId.getBytes(StandardCharsets.UTF_8)

  override def fromBytes(b: Array[Byte]): FindLeader = {
    val (heightBs, rest) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)

    val (commitedTxIndxBytes, sigAndnodeBs) = rest.splitAt(8)
    val committedTxIndex = Longs.fromByteArray(commitedTxIndxBytes)

    val (sigIndxBytes, nodeBs) = sigAndnodeBs.splitAt(4)
    val sigIndx = Ints.fromByteArray(sigIndxBytes)

    FindLeader(height, committedTxIndex, sigIndx, new String(nodeBs, StandardCharsets.UTF_8))
  }

}
