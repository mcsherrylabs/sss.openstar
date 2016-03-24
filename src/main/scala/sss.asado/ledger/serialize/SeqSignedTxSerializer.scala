package sss.asado.ledger.serialize

import com.google.common.primitives.Ints
import ledger.{SeqSignedTx, SignedTx, _}
import sss.asado.util.Serialize.Serializer
import scala.annotation.tailrec

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object SeqSignedTxSerializer extends Serializer[SeqSignedTx]{

  override def toBytes(seqStx: SeqSignedTx): Array[Byte] = {
    val numStxs = seqStx.ordered.size

    val allAsBytes = seqStx.ordered map { stx =>
      val asBytes = stx.toBytes
      val stxLen = asBytes.length
      Ints.toByteArray(stxLen) ++ asBytes
    }

    allAsBytes.foldLeft(Ints.toByteArray(numStxs))((acc, e) => acc ++ e)
  }

  @tailrec
  private def extractStx(numOfStxsRemaining: Int, bytes: Array[Byte], acc: Seq[SignedTx]): Seq[SignedTx] = {
    if(numOfStxsRemaining == 0) acc
    else {
      val (lenStxBytes, remaining) = bytes.splitAt(4)
      val lenStx = Ints.fromByteArray(lenStxBytes)
      val (stxBytes, otherStxs) = remaining.splitAt(lenStx)
      extractStx(numOfStxsRemaining - 1, otherStxs, acc :+ stxBytes.toSignedTx)
    }
  }

  override def fromBytes(b: Array[Byte]): SeqSignedTx = {
    val (lenOfStxAry, allMinusStxCount) = b.splitAt(4)
    val numOfStxs = Ints.fromByteArray(lenOfStxAry)
    val stxs = extractStx(numOfStxs, allMinusStxCount, Seq.empty)

    SeqSignedTx(stxs)
  }
}
