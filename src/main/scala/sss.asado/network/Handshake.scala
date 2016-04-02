package sss.asado.network

import com.google.common.primitives.{Ints, Longs}
import sss.ancillary.Logging

import scala.util.{Failure, Try}


case class Handshake(applicationName: String,
                     applicationVersion: ApplicationVersion,
                     fromAddress: String,
                     nodeId: String,
                     fromPort: Int,
                     fromNonce: Long,
                     time: Long
                    ) {

  lazy val bytes: Array[Byte] = {
    val anb = applicationName.getBytes
    val fab = fromAddress.getBytes
    val nodeIdb = nodeId.getBytes

    Array(anb.size.toByte) ++ anb ++
      Ints.toByteArray(nodeId.size) ++
      nodeIdb ++
      applicationVersion.bytes ++
      Ints.toByteArray(fab.size) ++ fab ++
      Ints.toByteArray(fromPort) ++
      Longs.toByteArray(fromNonce) ++
      Longs.toByteArray(time)
  }
}

object Handshake extends Logging {
  def parse(bytes: Array[Byte]): Try[Handshake] = Try {
    var position = 0
    val appNameSize = bytes.head
    position += 1

    val an = new String(bytes.slice(position, position + appNameSize))
    position += appNameSize

    val nodeIdSize = Ints.fromByteArray(bytes.slice(position, position + 4))
    position += 4

    val nodeId = new String(bytes.slice(position, position + nodeIdSize))

    position += nodeIdSize

    val av = ApplicationVersion.parse(bytes.slice(position, position + ApplicationVersion.SerializedVersionLength)).get
    position += ApplicationVersion.SerializedVersionLength

    val fas = Ints.fromByteArray(bytes.slice(position, position + 4))
    position += 4

    val fa = new String(bytes.slice(position, position + fas))
    position += fas

    val port = Ints.fromByteArray(bytes.slice(position, position + 4))
    position += 4

    val nonce = Longs.fromByteArray(bytes.slice(position, position + 8))
    position += 8

    val time = Longs.fromByteArray(bytes.slice(position, position + 8))

    Handshake(an, av, fa, nodeId, port, nonce, time)
  }.recoverWith { case t: Throwable =>
    log.info("Error during handshake parsing:", t)
    Failure(t)
  }
}