package sss.asado.network

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/3/16.
  */
object Message {
  type MessageCode = Byte

  val MAGIC = Array(0x12: Byte, 0x34: Byte, 0x56: Byte, 0x78: Byte)

  val MagicLength = MAGIC.length

  val ChecksumLength = 4
}