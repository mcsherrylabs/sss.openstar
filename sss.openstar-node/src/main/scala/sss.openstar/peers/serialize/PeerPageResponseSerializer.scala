package sss.openstar.peers.serialize

import sss.openstar.peers._
import sss.openstar.util.Serialize._


object PeerPageResponseSerializer extends Serializer[PeerPageResponse] {

  override def toBytes(p: PeerPageResponse): Array[Byte] = {
    StringSerializer(p.nodeId) ++
      ByteArraySerializer(p.sockAddr.toBytes) ++
      ByteArraySerializer(p.capabilities.toBytes)
        .toBytes
  }

  override def fromBytes(bs: Array[Byte]): PeerPageResponse = {
    PeerPageResponse.tupled (bs.extract(
      StringDeSerialize,
      ByteArrayDeSerialize(_.toInetSocketAddress),
      ByteArrayDeSerialize(_.toCapabilities)
    ))

  }
}
