package sss.asado.network

import com.google.common.primitives.{Ints, Longs}
import sss.ancillary.Logging

import scala.util.{Failure, Try}


case class Handshake(applicationName: String,
                     applicationVersion: ApplicationVersion,
                     fromAddress: String,
                     nodeId: String,
                     tag: String,
                     fromPort: Int,
                     fromNonce: Long,
                     sig: Array[Byte],
                     time: Long
                    ) {

  lazy val bytes: Array[Byte] = {
    val anb = applicationName.getBytes
    val fab = fromAddress.getBytes
    val nodeIdb = nodeId.getBytes
    val tagBs = tag.getBytes

    Array(anb.size.toByte) ++ anb ++
      Ints.toByteArray(nodeId.size) ++
      nodeIdb ++
      applicationVersion.bytes ++
      Ints.toByteArray(fab.size) ++ fab ++
      Ints.toByteArray(tagBs.size) ++ tagBs ++
      Ints.toByteArray(fromPort) ++
      Longs.toByteArray(fromNonce) ++
      Ints.toByteArray(sig.length) ++
      sig ++
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

    val tagSize = Ints.fromByteArray(bytes.slice(position, position + 4))
    position += 4

    val tag = new String(bytes.slice(position, position + tagSize))
    position += tagSize

    val port = Ints.fromByteArray(bytes.slice(position, position + 4))
    position += 4

    val nonce = Longs.fromByteArray(bytes.slice(position, position + 8))
    position += 8

    val sigSizeBytes = bytes.slice(position, position + 4)
    position += 4
    val sigsize = Ints.fromByteArray(sigSizeBytes)

    val sig = bytes.slice(position, position + sigsize)
    position += sigsize

    val time = Longs.fromByteArray(bytes.slice(position, position + 8))

    Handshake(an, av, fa, nodeId, tag, port, nonce, sig, time)
  }.recoverWith { case t: Throwable =>
    log.info("Error during handshake parsing:", t)
    Failure(t)
  }
}