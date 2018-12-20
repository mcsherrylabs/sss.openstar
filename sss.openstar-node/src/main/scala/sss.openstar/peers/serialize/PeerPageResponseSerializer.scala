package sss.openstar.peers.serialize

import java.net.{InetAddress, InetSocketAddress}

import sss.openstar.peers._
import sss.openstar.util.Serialize._
import sss.openstar.network._

object PeerPageResponseSerializer extends Serializer[PeerPageResponse] {

  private val ip4Prefix: Array[Byte] = Array.fill[Byte](10)(0) ++ Array.fill[Byte](2)(-1)

  override def toBytes(p: PeerPageResponse): Array[Byte] = {
    StringSerializer(p.nodeId) ++
      ByteArraySerializer((ip4Prefix ++ p.sockAddr.getAddress.getAddress).takeRight(16)) ++
      IntSerializer(p.sockAddr.getPort) ++
      ByteArraySerializer(p.capabilities.toBytes)
  }

  override def fromBytes(bs: Array[Byte]): PeerPageResponse = {
    val extracted = bs.extract(StringDeSerialize,
      ByteArrayDeSerialize(bs => InetAddress.getByAddress(bs)),
      IntDeSerialize,
      ByteArrayDeSerialize(_.toCapabilities)
    )

    PeerPageResponse(extracted._1,
      new InetSocketAddress(extracted._2, extracted._3),
      extracted._4
    )
  }
}
