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

  require(nodeId.nonEmpty, "Handshake node id cannot be an empty string.")

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
      (Handshake.apply _).tupled(
        bytes.extract(
          StringDeSerialize,
          ByteArrayDeSerialize(ApplicationVersion.parse),
          StringDeSerialize,
          StringDeSerialize,
          LongDeSerialize,
          ByteStringDeSerialize,
          LongDeSerialize
        )
      )
    }
}
