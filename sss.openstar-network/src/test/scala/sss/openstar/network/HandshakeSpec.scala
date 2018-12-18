package sss.openstar.network

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.crypto.SeedBytes

import scala.util.Success

class HandshakeSpec extends FlatSpec with Matchers {

  "Handshake " should " serialise and deserialize to same " in {

    val sig = ByteString(SeedBytes.randomSeed(10))
    val time = System.currentTimeMillis()
    val h = Handshake("appName",
                      ApplicationVersion(2, 3, 4),
                      "nodeId",
                      "tag",
                      Long.MaxValue,
                      sig,
                      time)

    val Success(deserialized) = Handshake.parse(h.bytes)

    assert(h === deserialized)

  }

}
