package sss.asado.block.serialize


import java.nio.charset.StandardCharsets

import com.google.common.primitives.{Ints, Longs}
import org.joda.time.DateTime
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object BlockSignatureSerializer extends Serializer[BlockSignature]{

  override def toBytes(blockSig: BlockSignature): Array[Byte] = {
    val heightBytes = Longs.toByteArray(blockSig.height)
    val nodeIdBytes = blockSig.nodeId.getBytes(StandardCharsets.UTF_8)
    val nodeIdBytesLenBytes = Ints.toByteArray(nodeIdBytes.length)
    val pkLen = blockSig.publicKey.length
    val sigLen = blockSig.signature.length

    heightBytes ++
      nodeIdBytesLenBytes ++
      nodeIdBytes ++
      Ints.toByteArray(pkLen) ++ blockSig.publicKey ++
      Ints.toByteArray(sigLen) ++ blockSig.signature ++
      Ints.toByteArray(blockSig.index) ++
      Longs.toByteArray(blockSig.savedAt.getMillis)

  }

  override def fromBytes(b: Array[Byte]): BlockSignature = {
    val (heightBs, rest) = b.splitAt(8)
    val h = Longs.fromByteArray(heightBs)
    val (nodeIdLenBs, more) = rest.splitAt(4)
    val nodeIdlen = Ints.fromByteArray(nodeIdLenBs)
    val (nodeIdBs, evenMore) = more.splitAt(nodeIdlen)
    val nodeId = new String(nodeIdBs, StandardCharsets.UTF_8)

    val (lenBs, yetMore) = evenMore.splitAt(4)
    val len = Ints.fromByteArray(lenBs)
    val(pk, sigPlus) = yetMore.splitAt(len)
    val (sigLenBs, nearlyThere) = sigPlus.splitAt(4)
    val sigLen = Ints.fromByteArray(sigLenBs)
    val (sig, indexAndHeightBs) = nearlyThere.splitAt(sigLen)
    val(indxBs, savedAtBs) = indexAndHeightBs.splitAt(4)

    BlockSignature(Ints.fromByteArray(indxBs),
      new DateTime(Longs.fromByteArray(savedAtBs)),
      h, nodeId, pk, sig)
  }

}
