package sss.asado.ledger.serialize

import com.google.common.primitives.Ints
import ledger._

import scala.annotation.tailrec
import sss.asado.util.Serialize.Serializer
/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */

object SignedTxSerializer extends Serializer[SignedTx] {


  override def toBytes(t: SignedTx): Array[Byte] = {

    val len = t.params.length

    val allParamsBytes = t.params.foldLeft(Ints.toByteArray(len))((acc, e) => {
      acc ++ Ints.toByteArray(e.length) ++ e
    })

    Ints.toByteArray(allParamsBytes.length) ++ allParamsBytes ++ t.tx.toBytes
  }

  @tailrec
  private def extractParams(count: Int, b: Array[Byte], acc: Seq[Array[Byte]]): Seq[Array[Byte]] = {
    require(count >= 0)

    if(count == 0) acc
    else {
      val (param, remaining) = extractParam(b)
      extractParams(count - 1, remaining, acc :+ param)
    }
  }

  private def extractParam(b: Array[Byte]): (Array[Byte], Array[Byte]) = {
    val (lenParamBytes, allMinusLen) = b.splitAt(4)
    val lenParam = Ints.fromByteArray(lenParamBytes)
    allMinusLen.splitAt(lenParam)
  }

  override def fromBytes(b: Array[Byte]): SignedTx = {
    val (lenParamsBytes, allMinusLen) = b.splitAt(4)
    val lenParams = Ints.fromByteArray(lenParamsBytes)
    val (paramsBytes, txBytes) = allMinusLen.splitAt(lenParams)
    val tx = txBytes.toTx

    val (numParamsBytes, paramsBytesMinusNum) = paramsBytes.splitAt(4)
    val numParams = Ints.fromByteArray(numParamsBytes)
    val params = extractParams(numParams, paramsBytesMinusNum, Seq())
    SignedTx(tx, params)
  }
}
