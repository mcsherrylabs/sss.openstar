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

    val num = t.params.length

    val allParamsBytes = t.params.foldLeft(Ints.toByteArray(num))((acc, e) => {
      val asBytes = seqToBytes(e)
      acc ++ Ints.toByteArray(asBytes.length) ++ asBytes
    })

    Ints.toByteArray(allParamsBytes.length) ++ allParamsBytes ++ t.tx.toBytes
  }

  private def seqToBytes(param: Seq[Array[Byte]]): Array[Byte] = {
    val num = param.length
    param.foldLeft(Ints.toByteArray(num))((acc, e) => {
      acc ++ Ints.toByteArray(e.length) ++ e
    })

  }
  private def bytesToSeq(param: Array[Byte]): Seq[Array[Byte]] = {
    val (numParamBytes, allMinusNum) = param.splitAt(4)
    val numParams = Ints.fromByteArray(numParamBytes)

    def extract(count: Int, b: Array[Byte], acc: Seq[Array[Byte]]): Seq[Array[Byte]] = {
      if(count == 0) acc
      else {
        val pair = extractParam(b)
        extract(count - 1, pair._2, acc ++ Seq(pair._1))
      }
    }
    extract(numParams, allMinusNum, Seq())
  }


  @tailrec
  private def extractParams(count: Int, b: Array[Byte], acc: Seq[Seq[Array[Byte]]]): Seq[Seq[Array[Byte]]] = {
    require(count >= 0)

    if(count == 0) acc
    else {
      val (param, remaining) = extractParam(b)
      extractParams(count - 1, remaining, acc :+ bytesToSeq(param))
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
