package sss.asado.network

import java.nio.ByteBuffer

import akka.util.ByteString
import com.google.common.primitives.{Bytes, Ints}
import scorex.crypto.hash.Blake256

import scala.util.Try

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object Protocol {
  type MessageCode = Byte

  val MAGIC = Array(0x12: Byte, 0x34: Byte, 0x56: Byte, 0x78: Byte)

  val MagicLength = MAGIC.length

  val ChecksumLength = 4
}

trait Protocol {

  def toWire(networkMessage: NetworkMessage):ByteString = {
    toWire(networkMessage.msgCode, networkMessage.data)
  }

  def toWire(msgCode: Byte, dataBytes:Array[Byte]):ByteString = {

    val dataLength: Int = dataBytes.length
    val dataWithChecksum = if (dataLength > 0) {
      val checksum = Blake256.hash(dataBytes).take(Protocol.ChecksumLength)
      Bytes.concat(checksum, dataBytes)
    } else dataBytes //empty array

    val bytes = Protocol.MAGIC ++ Array(msgCode) ++ Ints.toByteArray(dataLength) ++ dataWithChecksum
    ByteString(Ints.toByteArray(bytes.length) ++ bytes)
  }

  def fromWire(bytes: ByteBuffer): Try[NetworkMessage] = Try {
    val magic = new Array[Byte](Protocol.MagicLength)
    bytes.get(magic)

    assert(magic.sameElements(Protocol.MAGIC), "Wrong magic bytes" + magic.mkString)

    val msgCode = bytes.get

    val length = bytes.getInt
    assert(length >= 0, "Data length is negative!")

    val data = new Array[Byte](length)

    if(length > 0) {

      //READ CHECKSUM
      val checksum = new Array[Byte](Protocol.ChecksumLength)
      bytes.get(checksum)

      //READ DATA
      bytes.get(data)

      //VALIDATE CHECKSUM
      val digest = Blake256.hash(data).take(Protocol.ChecksumLength)

      //CHECK IF CHECKSUM MATCHES
      assert(checksum.sameElements(digest), s"Invalid data checksum length = $length")
    }

    NetworkMessage(msgCode, data)
  }
}