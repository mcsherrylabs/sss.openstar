package sss.asado.block.serialize

import block.GetTxPage
import com.google.common.primitives.{Ints, Longs}
import sss.asado.util.Serialize.Serializer

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object GetTxPageSerializer extends Serializer[GetTxPage]{

  override def toBytes(getTxPage: GetTxPage): Array[Byte] = Longs.toByteArray(getTxPage.blockHeight) ++
    Longs.toByteArray(getTxPage.index) ++
    Ints.toByteArray(getTxPage.pageSize)

  override def fromBytes(b: Array[Byte]): GetTxPage = {
    val (heightBs, rest) = b.splitAt(8)
    val height = Longs.fromByteArray(heightBs)
    val (indxBytes, rest2) = rest.splitAt(8)
    val indx = Longs.fromByteArray(indxBytes)
    val pageSize = Ints.fromByteArray(rest2)
    GetTxPage(height, indx,pageSize)
  }

}
