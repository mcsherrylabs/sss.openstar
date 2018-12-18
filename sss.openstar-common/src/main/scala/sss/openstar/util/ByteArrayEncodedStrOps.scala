package sss.openstar.util

import java.util.Base64

/**
  * Created by alan on 4/5/16.
  */
object ByteArrayEncodedStrOps {

  implicit class ByteArrayToBase64UrlStr(bs: Array[Byte]) {
    def toBase64Str: String = Base64.getUrlEncoder.withoutPadding.encodeToString(bs)
  }
  implicit class Base64StrToByteArray(hex:String) {
    def toByteArray: Array[Byte]= Base64.getUrlDecoder.decode(hex)
  }

}
