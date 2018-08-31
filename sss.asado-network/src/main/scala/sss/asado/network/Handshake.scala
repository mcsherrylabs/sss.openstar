package sss.asado.network

import akka.util.ByteString
import sss.ancillary.Logging
import sss.asado.util.Serialize._

import scala.util.Try

case class Handshake(applicationName: String,
                     applicationVersion: ApplicationVersion,
                     nodeId: String,
                     tag: String,
                     fromNonce: Long,
                     sig: ByteString,
                     time: Long) {

  lazy val byteString: ByteString = ByteString(bytes)

  lazy val bytes: Array[Byte] = {
    StringSerializer(applicationName) ++
      ByteArraySerializer(applicationVersion.bytes) ++
      StringSerializer(nodeId) ++
      StringSerializer(tag) ++
      LongSerializer(fromNonce) ++
      ByteStringSerializer(sig) ++
      LongSerializer(time).toBytes
  }
}

object Handshake extends Logging {
  def parse(bytes: Array[Byte]): Try[Handshake] =
    Try {
      val extracted = bytes.extract(
        StringDeSerialize,
        ByteArrayDeSerialize,
        StringDeSerialize,
        StringDeSerialize,
        LongDeSerialize,
        ByteStringDeSerialize,
        LongDeSerialize
      )
      Handshake(
        extracted._1,
        ApplicationVersion.parse(extracted._2),
        extracted._3,
        extracted._4,
        extracted._5,
        extracted._6,
        extracted._7
      )
    }
}
