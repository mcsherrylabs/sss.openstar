package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}
import sss.db.Db
import sss.openstar.network.NodeId
import sss.openstar.peers.Discovery.DiscoveredNode
import sss.openstar.util.hash.FastCryptographicHash

class DiscoverySpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val db: Db = Db()

  val inet = InetAddress.getLocalHost
  val inetSocket = new InetSocketAddress(inet, 8080)
  val inetSocket2 = new InetSocketAddress(inet, 8081)
  val inetSocket3 = new InetSocketAddress(inet, 8083)
  val inetSocket4 = new InetSocketAddress(inet, 8084)
  val caps = 34.toByte
  val sut = new Discovery(FastCryptographicHash.hash)

  override def afterAll: Unit = {
    sut.purge(caps)
  }

  val someNode = "someNode"
  val otherNode = "otherNode"

  val n = NodeId(someNode, inetSocket)
  val otherN = NodeId(otherNode, inetSocket)

  "Discovery" should "save and lookup" in {

    sut.persist(n, caps)

    val results = sut.lookup(Set(n.id, "random"))
    assert(results.size === 1)
    assert(results.head.nodeId.id === n.id)
    assert(results.head.nodeId.inetSocketAddress === n.inetSocketAddress)

  }

  it should "find" in {
    val results = sut.find( 1, caps)
    assert(results.size == 1)
  }

  it should "not find an ignored id  " in {
    val results = sut.find(1, caps, Set(n.id, "random"))
    assert(results.size == 0)
  }

  it should "not find an id with wrong capabilities" in {
    val results = sut.find(1, 0.toByte)
    assert(results.isEmpty)
  }

  it should "not find an id when limit is 0" in {
    val results = sut.find(0, caps, Set(n.id))
    assert(results.isEmpty)
  }

  it should "not allow duplicate ip addr port combinations" in {

    val results = sut.find( 10, caps)
    assert(results.size == 1)
    val nodeWithSameInet = NodeId(otherNode, inetSocket)
    sut.persist(nodeWithSameInet, caps)
    val resultOneOnly = sut.lookup(Set(someNode, otherNode))
    assert(resultOneOnly === Seq(DiscoveredNode(otherN,caps)))
  }


  it should "change the checksum on new entry" in {
    val (_, chksum)= sut.query(0,10)
    val nodeWithSameInet = NodeId(someNode, inetSocket2)
    sut.persist(nodeWithSameInet, caps)
    val (_, chksum2)= sut.query(0,10)
    val (_, chksum3)= sut.query(0,1)

    assert(chksum !== chksum2)
    assert(chksum === chksum3)
  }

  it should "only prune beyond a given minimum number of records" in {
    val purgedCount = sut.prune(Int.MaxValue)
    assert(purgedCount === 0)
  }

  it should "prune the unreachable records first and change checksum" in {
    sut.unreachable(inetSocket2)
    val (_, chksum)= sut.query(0,10)
    val purgedCount = sut.prune(1)
    assert(purgedCount === 1)
    val found = sut.find(10, caps)
    assert(found.nonEmpty, s"$inetSocket should exist")
    assert(found.head.nodeId.inetSocketAddress.getPort === inetSocket.getPort)

    val (_, chksum2)= sut.query(0,10)
    assert(chksum !== chksum2)
  }

  it should "allow unreachable records to become reachable again" in {
    val node1 = NodeId(someNode + "1", inetSocket2)
    sut.persist(node1, caps)
    val node2 = NodeId(someNode + "2", inetSocket3)
    sut.persist(node2, caps)
    val node3 = NodeId(someNode + "3", inetSocket4)
    sut.persist(node3, caps)

    sut.unreachable(inetSocket2)
    sut.unreachable(inetSocket3)
    sut.unreachable(inetSocket4)

    sut.reachable(inetSocket3)

    val purgedCount = sut.prune(1)

    val found = sut.find(10, caps)
    assert(found.size === 1, s"$inetSocket3 should exist")
    assert(found.head.nodeId.inetSocketAddress.getPort === inetSocket3.getPort)

  }

}
