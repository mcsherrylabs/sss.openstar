package sss.asado.ledger.serialize

import com.google.common.primitives.{Bytes, Ints}
import ledger._

import scala.annotation.tailrec

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */

object TxSerializer extends Serializer[Tx] {

  private def toBytes(inOuts: (Seq[TxInput], Seq[TxOutput])): Array[Byte] = {

    val insBytes = inOuts._1.foldLeft(Array[Byte]())((bs, txInput) => {
      val inputBytes = TxInputSerializer.toBytes(txInput)
      Bytes.concat(bs, Ints.toByteArray(inputBytes.length), inputBytes)
    })

    val outBytes = inOuts._2.foldLeft(Array[Byte]())((bs, txOutput) => {
      val outputBytes = TxOutputSerializer.toBytes(txOutput)
      Bytes.concat(bs, Ints.toByteArray(outputBytes.length), outputBytes)
    })

    Ints.toByteArray(inOuts._1.length) ++ insBytes ++ Ints.toByteArray(inOuts._2.length) ++ outBytes

  }

  private def getInput(b: Array[Byte]): (TxInput, Array[Byte]) = {
    val (lenBytes, rest) = b.splitAt(4)
    val len = Ints.fromByteArray(lenBytes)
    val (inputBytes, remaining) = rest.splitAt(len)
    (TxInputSerializer.fromBytes(inputBytes), remaining)
  }

  @tailrec
  private def extractInputs(count: Int, b: Array[Byte], acc: Seq[TxInput]): (Seq[TxInput], Array[Byte])= {
    require(count >= 0)

    if(count == 0) (acc, b)
    else {
      val (in, rest) = getInput(b)
      extractInputs(count - 1, rest, acc :+ in)
    }
  }

  private def getOutput(b: Array[Byte]): (TxOutput, Array[Byte]) = {
    val (lenBytes, rest) = b.splitAt(4)
    val len = Ints.fromByteArray(lenBytes)
    val (outputBytes, remaining) = rest.splitAt(len)
    (TxOutputSerializer.fromBytes(outputBytes), remaining)
  }

  @tailrec
  private def extractOutputs(count: Int, b: Array[Byte], acc: Seq[TxOutput]): (Seq[TxOutput], Array[Byte])= {
    require(count >= 0)

    if(count == 0) (acc, b)
    else {
      val (in, rest) = getOutput(b)
      extractOutputs(count - 1, rest, acc :+ in)
    }
  }

  override def toBytes(t: Tx): Array[Byte] = {
    toBytes(t.ins, t.outs)
  }

  override def fromBytes(b: Array[Byte]): Tx = {

    val (lenOfInsAry, allMinusInsCount) = b.splitAt(4)
    val numOfIns = Ints.fromByteArray(lenOfInsAry)
    val (ins, remaining) = extractInputs(numOfIns, allMinusInsCount, Seq.empty)

    val (numOfOutsAry, allOuts) = remaining.splitAt(4)
    val numOfOuts = Ints.fromByteArray(numOfOutsAry)

    val (outs, nowt) = extractOutputs(numOfOuts, allOuts, Seq.empty)
    if(ins.length == 0) GenisesTx(outs = outs)
    else StandardTx(ins, outs)
  }
}
