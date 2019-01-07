package sss.openstar.peers.serialize

import java.net.{InetAddress, InetSocketAddress}

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.peers.PeerPage

class PeerPageSerializerSpec extends FlatSpec with Matchers {

  "An PeerPageSerializerSerializer " should " be able to serialize and deserialize " in {

    val p = PeerPage(0, 50000, ByteString("Hello Nurse"))
    val asBytes = PeerPageSerializer.toBytes(p)
    val p2 = PeerPageSerializer.fromBytes(asBytes)

    assert(p === p2)
  }

}
