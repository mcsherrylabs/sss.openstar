package sss.asado.util

import javax.xml.bind.DatatypeConverter

/**
  * Created by alan on 4/5/16.
  */
object ByteArrayVarcharOps {

  //implicit def toByteAry(hex:String): Array[Byte] = DatatypeConverter.parseHexBinary(hex)
  //implicit def tovarChar(bs: Array[Byte]): String = DatatypeConverter.printHexBinary(bs)

  implicit class ByteArrayToVarChar(bs: Array[Byte]) {
    def toVarChar: String = DatatypeConverter.printHexBinary(bs)
  }
  implicit class VarCharToByteArray(hex:String) {
    def toByteArray: Array[Byte]= DatatypeConverter.parseHexBinary(hex)
  }

}
