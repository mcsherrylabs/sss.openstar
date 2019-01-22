package sss.openstar.peers.serialize

import java.net.{InetAddress, InetSocketAddress}

import org.scalatest.{FlatSpec, Matchers}

class InetSocketAddressSerializerSpec extends FlatSpec with Matchers {

  "An InetAddressSocketSerializer " should " be able to serialize and deserialize " in {

    val testList = InetAddress.getAllByName("www.google.at")
    val socketTestList = testList.indices map { i =>
      new InetSocketAddress(testList(i), 1000 + i)
    }

    val asBytes = socketTestList.map (InetSocketAddressSerializer.toBytes(_))
    val deserialized = asBytes map (InetSocketAddressSerializer.fromBytes(_))

    deserialized foreach (println)
    socketTestList foreach (println)


    val allTrue = deserialized zip socketTestList map { zipped =>
      zipped._1.getPort === zipped._2.getPort &&
        zipped._1.getAddress === zipped._2.getAddress
    }

    assert(!allTrue.contains(false))
  }

}
