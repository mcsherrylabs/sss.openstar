package sss.openstar.peers.serialize

import java.net.InetAddress

import org.scalatest.{FlatSpec, Matchers}

class InetAddressSerializerSpec extends FlatSpec with Matchers {

  "An InetAddressSerializer " should " be able to serialize and deserialize " in {

    val testList = InetAddress.getAllByName("www.google.at")

    val asBytes = testList map (InetAddressSerializer.toBytes)
    val deserialized = asBytes map (InetAddressSerializer.fromBytes)

    deserialized foreach (println)
    testList foreach (println)

    val allTrue = deserialized zip testList map (zipped => zipped._1.getAddress === zipped._2.getAddress)
    assert(!allTrue.contains(false))
  }

}
