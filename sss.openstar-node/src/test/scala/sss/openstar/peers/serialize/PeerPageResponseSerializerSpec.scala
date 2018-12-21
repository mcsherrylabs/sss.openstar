package sss.openstar.peers.serialize

import java.net.{InetAddress, InetSocketAddress}

import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.peers.{Capabilities, PeerPageResponse}

class PeerPageResponseSerializerSpec extends FlatSpec with Matchers {

  "An PeerResponseSerializerSerializer " should " be able to serialize and deserialize " in {

    val socketAddr = new InetSocketAddress(InetAddress.getByName("www.google.at"), 8000)
    val p = PeerPageResponse("Hello", socketAddr, Capabilities(23.toByte))
    val asBytes = PeerPageResponseSerializer.toBytes(p)
    val p2 = PeerPageResponseSerializer.fromBytes(asBytes)

    assert(p === p2)
  }

}
