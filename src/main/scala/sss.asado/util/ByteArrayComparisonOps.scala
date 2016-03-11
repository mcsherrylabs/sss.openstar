package sss.asado.util

import scala.collection.mutable.WrappedArray

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/11/16.
  */
trait ByteArrayComparisonOps {

  implicit class ByteArrayComparison(ary: Array[Byte]) {
    def isSame(otherAry: WrappedArray[Byte]): Boolean = otherAry == ary.toSeq
  }

  implicit class SeqByteArrayComparison(ary: Seq[Array[Byte]]) {
    def isSame(otherAry: Seq[Array[Byte]]): Boolean = otherAry.corresponds(ary)(_ isSame _ )
  }
}
