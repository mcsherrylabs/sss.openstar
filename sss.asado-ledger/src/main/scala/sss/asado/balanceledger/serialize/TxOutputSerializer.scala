package sss.asado.balanceledger.serialize

import com.google.common.primitives.Ints
import sss.asado.contract.Encumbrance
import sss.asado.balanceledger._
import sss.asado.contract.ContractSerializer._
import sss.asado.util.Serialize.Serializer
/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object TxOutputSerializer extends Serializer[TxOutput]{

  override def toBytes(t: TxOutput): Array[Byte] = {

    val amountBytes = Ints.toByteArray(t.amount)
    val encBytes = t.encumbrance.toBytes
    val encBytesLen = encBytes.length
    amountBytes ++ Ints.toByteArray(encBytesLen) ++ encBytes
  }

  override def fromBytes(b: Array[Byte]): TxOutput = {

    val (amountBytes, encumbrancePlusLen) = b.splitAt(4)
    val amount = Ints.fromByteArray(amountBytes)
    val (encumbranceLenBytes, encumbranceBytes) = encumbrancePlusLen.splitAt(4)
    val encumbranceLen = Ints.fromByteArray(encumbranceLenBytes)

    val encumbrance = encumbranceBytes.toEncumbrance
    TxOutput(amount, encumbrance)
  }
}

