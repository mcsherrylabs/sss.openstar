package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import sss.db.Db
import sss.openstar.network.NodeId
import sss.openstar.util.hash.FastCryptographicHash

class DiscoveryImplSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val db: Db = Db()

  val inet = InetAddress.getLocalHost
  val inetSocket = new InetSocketAddress(inet, 8080)
  val caps = 34.toByte
  val sut = new Discovery(FastCryptographicHash.hash)

  override def afterAll: Unit = {
    sut.purge(caps)
  }


  val n = NodeId("aldksjaldksjald", inetSocket)

  "DiscoveryImpl" should " save and lookup " in {

    sut.insert(n, caps)

    val results = sut.lookup(Set(n.id, "random"))
    assert(results.size === 1)
    assert(results.head.nodeId.id === n.id)
    assert(results.head.nodeId.inetSocketAddress === n.inetSocketAddress)

  }

  it should " find " in {
    val results = sut.find(Set("dasf"), 1, caps)
    assert(results.size == 1)
  }

  it should " not find an ignored id  " in {
    val results = sut.find(Set(n.id, "random"), 1, caps)
    assert(results.size == 0)
  }

  it should " not find an id with wrong capabilities " in {
    val results = sut.find(Set("random"), 1, 0.toByte)
    assert(results.size == 0)
  }

  it should " not find an id when limit is 0 " in {
    val results = sut.find(Set(n.id), 0, caps)
    assert(results.size == 0)
  }
}
