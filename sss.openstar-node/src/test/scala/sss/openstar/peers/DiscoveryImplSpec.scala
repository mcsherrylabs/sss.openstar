package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}

import org.scalatest.{FlatSpec, Matchers}
import sss.db.Db
import sss.openstar.network.NodeId

class DiscoveryImplSpec extends FlatSpec with Matchers {

  implicit val db: Db = Db()
  val sut = new DiscoveryImpl()

  val inet = InetAddress.getLocalHost
  val inetSocket = new InetSocketAddress(inet, 8080)
  val caps = 34.toByte

  val n = NodeId("aldksjaldksjald", inetSocket)

  "DiscoveryImpl" should " save and lookup " in {

    sut.insert(n, caps)

    val results = sut.lookup(Set(n.id, "random"))
    assert(results.size === 1)
    assert(results.head.id === n.id)
    assert(results.head.inetSocketAddress === n.inetSocketAddress)

  }

  it should " find " in {
    val results = sut.find(Set.empty, 1, caps)
    assert(results.size == 1)
  }
  it should " find an ignored id  " in {
    val results = sut.find(Set(n.id, "random"), 1, caps)
    assert(results.size == 0)
  }

  it should " find an id with wrong capabilities " in {
    val results = sut.find(Set("random"), 1, 0.toByte)
    assert(results.size == 0)
  }

  it should " find an id when limit is 0 " in {
    val results = sut.find(Set(n.id), 0, caps)
    assert(results.size == 0)
  }
}
