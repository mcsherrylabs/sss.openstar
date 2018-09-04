package sss.asado.handshake

import akka.util.ByteString
import sss.ancillary.Logging
import sss.asado.network.ApplicationVersion
import sss.asado.util.Serialize._
import sss.asado.{Identity, IdentityTag}

import scala.util.Try

case class Handshake(applicationName: String,
                     applicationVersion: ApplicationVersion,
                     nodeId: Identity,
                     tag: IdentityTag,
                     fromNonce: Long,
                     sig: ByteString,
                     time: Long) {

  lazy val byteString: ByteString = ByteString(bytes)

  lazy val bytes: Array[Byte] = {
    StringSerializer(applicationName) ++
      ByteArraySerializer(applicationVersion.bytes) ++
      StringSerializer(nodeId.value) ++
      StringSerializer(tag.value) ++
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
        StringDeSerialize(Identity),
        StringDeSerialize(IdentityTag),
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
