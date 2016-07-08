package sss.asado.balanceledger.serialize

import com.google.common.primitives.Ints
import sss.asado.contract.Decumbrance
import sss.asado.balanceledger.{TxIndexLen, TxInput}
import sss.asado.contract.ContractSerializer._
import sss.asado.util.Serialize.Serializer
/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object TxInputSerializer extends Serializer[TxInput]{

  override def toBytes(t: TxInput): Array[Byte] = {

    val indxBytes = t.txIndex.toBytes
    val amountBytes = Ints.toByteArray(t.amount)
    val sigBytes = t.sig.toBytes
    val sigBytesLen = sigBytes.length

    indxBytes ++ amountBytes ++ Ints.toByteArray(sigBytesLen) ++ sigBytes
  }

  override def fromBytes(b: Array[Byte]): TxInput = {
    val (txIndexBytes, rest) = b.splitAt(TxIndexLen)
    val indx = TxIndexSerializer.fromBytes(txIndexBytes)
    val (amountBytes, dencumbrancePlusLen) = rest.splitAt(4)
    val amount = Ints.fromByteArray(amountBytes)
    val (dencumbranceLenBytes, dencumbranceBytes) = dencumbrancePlusLen.splitAt(4)
    val dencumbranceLen = Ints.fromByteArray(dencumbranceLenBytes)
    val dencumbrance = dencumbranceBytes.toDecumbrance
    TxInput(indx, amount, dencumbrance)
  }
}
