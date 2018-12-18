package sss.openstar.util

import com.google.common.primitives.Ints
import sss.openstar.util.Serialize.{Serializer, ToBytes}

import scala.annotation.tailrec

object SeqSerializer extends Serializer[Iterable[Array[Byte]]] {

  implicit class SeqToBytes[T <% ToBytes](val s: Seq[T]) extends ToBytes {
    override def toBytes: Array[Byte] = SeqSerializer.toBytes(s map (_.toBytes))
  }

  override def toBytes(seqAryBytes: Iterable[Array[Byte]]): Array[Byte] = {
    val num = seqAryBytes.size

    val allAsBytes = seqAryBytes map { bytes =>
      val len = bytes.length
      Ints.toByteArray(len) ++ bytes
    }

    allAsBytes.foldLeft(Ints.toByteArray(num))((acc, e) => acc ++ e)
  }

  @tailrec
  private def extract(numByteArysRemaining: Int, bytes: Array[Byte], acc: Seq[Array[Byte]]): (Seq[Array[Byte]], Array[Byte]) = {
    if(numByteArysRemaining == 0) (acc, bytes)
    else {
      val (lenBytesInBytes, remaining) = bytes.splitAt(4)
      val lenBytes = Ints.fromByteArray(lenBytesInBytes)
      val (oneBytes, otherBytes) = remaining.splitAt(lenBytes)
      extract(numByteArysRemaining - 1, otherBytes, acc :+ oneBytes)
    }
  }

  override def fromBytes(b: Array[Byte]): Seq[Array[Byte]] = {
    val (lenOfStxAry, allMinusStxCount) = b.splitAt(4)
    val numOfStxs = Ints.fromByteArray(lenOfStxAry)
    extract(numOfStxs, allMinusStxCount, Seq.empty)._1
  }

  def fromBytesWithRemainder(b: Array[Byte]): (Seq[Array[Byte]], Array[Byte]) = {
    val (lenOfStxAry, allMinusStxCount) = b.splitAt(4)
    val numOfStxs = Ints.fromByteArray(lenOfStxAry)
    extract(numOfStxs, allMinusStxCount, Seq.empty)
  }
}