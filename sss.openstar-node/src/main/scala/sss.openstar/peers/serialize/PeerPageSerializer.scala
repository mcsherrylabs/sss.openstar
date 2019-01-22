package sss.openstar.peers.serialize

import sss.openstar.peers.PeerPage
import sss.openstar.util.Serialize._

object PeerPageSerializer extends Serializer[PeerPage] {

  override def toBytes(p: PeerPage): Array[Byte] = {

    IntSerializer(p.start) ++
    IntSerializer(p.end) ++
    ByteStringSerializer(p.fingerprint)
      .toBytes

  }

  override def fromBytes(bs: Array[Byte]): PeerPage = {
    PeerPage.tupled(
      bs.extract(IntDeSerialize, IntDeSerialize, ByteStringDeSerialize)
    )
  }
}
