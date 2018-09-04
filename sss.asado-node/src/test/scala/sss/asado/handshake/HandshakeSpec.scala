package sss.asado.handshake

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.{Identity, IdentityTag}
import sss.asado.crypto.SeedBytes
import sss.asado.network.ApplicationVersion

import scala.util.Success

class HandshakeSpec extends FlatSpec with Matchers {

  "Handshake " should " serialise and deserialize to same " in {

    val sig = ByteString(SeedBytes.randomSeed(10))
    val time = System.currentTimeMillis()
    val h = Handshake("appName",
                      ApplicationVersion(2, 3, 4),
                      Identity("nodeId"),
                      IdentityTag("tag"),
                      Long.MaxValue,
                      sig,
                      time)

    val Success(deserialized) = Handshake.parse(h.bytes)

    assert(h === deserialized)

  }

}
