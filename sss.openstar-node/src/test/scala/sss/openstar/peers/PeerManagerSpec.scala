package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpecLike}

import sss.db.Db
import sss.openstar.network._
import sss.openstar.nodebuilder._
import sss.openstar.peers.Discovery.DiscoveredNode
import sss.openstar.peers.PeerManager.ChainQuery
import sss.openstar.util.hash.FastCryptographicHash
import sss.openstar._

import scala.concurrent.duration._
import scala.language.postfixOps


class PeerManagerSpec extends TestKit(TestUtils.actorSystem) with WordSpecLike with Matchers with ImplicitSender {

  private val chainId = 1.toByte

  private case class AttemptedConnection(toNode: UniqueNodeIdentifier) extends OpenstarEvent

  private object TestSystem extends TestSystem

  implicit val db = Db()

  import TestSystem.messageEventBus

  messageEventBus.subscribe(classOf[AttemptedConnection])

  val connect: NetConnect = new NetConnect {
    override def connect(nId: NodeId, reconnectStrategy: ReconnectionStrategy): Unit = {
      messageEventBus publish AttemptedConnection(nId.id)
    }
  }

  val bootstrapNodes: Set[DiscoveredNode] = ((0 until 2) map { i => DiscoveredNode(NodeId(s"node$i",
    new InetSocketAddress(
      InetAddress.getByName(s"127.0.0.$i"),
      80 + i)),
    i.toByte)
  }).toSet

  val ourCapabilities: Capabilities = Capabilities(chainId)
  val discoveryInterval: FiniteDuration = 15.seconds
  val discovery: Discovery = new Discovery(FastCryptographicHash.hash)

  val nodeId:UniqueNodeIdentifier = "yada"
  val ourNetAddress: InetSocketAddress = new InetSocketAddress(InetAddress.getLocalHost, 8090)

  val sut = new PeerManager(connect,
    TestSystem.netSend,
    bootstrapNodes,
    ourCapabilities,
    discoveryInterval,
    discovery,
    nodeId,
    ourNetAddress
  )

  "PeerManager" should {

    "attempt to connect to bootstrap when queried" in {
      sut.addQuery(ChainQuery(chainId, 2))
      val t = expectMsgType[AttemptedConnection]
      assert(bootstrapNodes.map(_.nodeId.id)contains(t.toNode))
    }

  }

}
