package sss.asado.block.serialize

import block.DistributeClose
import com.google.common.primitives.Ints
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.util.Serialize.Serializer

import scala.annotation.tailrec

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object DistributeCloseSerializer extends Serializer[DistributeClose]{

  override def toBytes(blockSig: DistributeClose): Array[Byte] = {

    val lenSigs = blockSig.blockSigs.length
    val seqOfBytes = blockSig.blockSigs.map(BlockSignatureSerializer.toBytes(_))
    val allSigsSerialized = seqOfBytes.foldLeft(Ints.toByteArray(lenSigs)){
      (acc, e) => acc ++ Ints.toByteArray(e.length) ++ e
    }
    allSigsSerialized ++ blockSig.blockId.toBytes
  }

  @tailrec
  private def extractSigs(acc: Seq[BlockSignature], numLeftToExtract: Int, rest: Array[Byte]):
                (Seq[BlockSignature], Array[Byte]) = {

    if(numLeftToExtract == 0) (acc, rest)
    else {
      val (lenSigBytes, rest2 ) = rest.splitAt(4)
      val lenSig = Ints.fromByteArray(lenSigBytes)
      val (sigBytes, rest3) = rest2.splitAt(lenSig)
      val blkSig = BlockSignatureSerializer.fromBytes(sigBytes)
      extractSigs(acc :+ blkSig, numLeftToExtract - 1, rest3)
    }
  }
  override def fromBytes(b: Array[Byte]): DistributeClose = {
    val (numSigsBytes, rest ) = b.splitAt(4)
    val numSigs = Ints.fromByteArray(numSigsBytes)
    val (seqSigs, rest2) = extractSigs(Seq(), numSigs, rest)
    DistributeClose(seqSigs, BlockIdSerializer.fromBytes(rest2))
  }

}
