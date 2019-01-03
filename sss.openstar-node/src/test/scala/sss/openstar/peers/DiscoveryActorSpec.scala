package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}

import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{ Matchers, WordSpecLike}

import sss.db.Db
import sss.openstar.TestUtils.TestIncoming
import sss.openstar.{MessageKeys,  TestSystem, TestUtils}
import sss.openstar.network.{ NodeId, SerializedMessage}
import sss.openstar.network.TestMessageEventBusOps._
import sss.openstar.peers.Discovery.DiscoveredNode
import sss.openstar.util.hash.FastCryptographicHash

import scala.language.postfixOps
import scala.util.Random

class DiscoveryActorSpec extends TestKit(TestUtils.actorSystem) with WordSpecLike with Matchers with ImplicitSender {

  private val chainId = 1.toByte

  private val probe1 = TestProbe()

  private val probe2 = TestProbe()
  private val observer1 = probe1.ref

  private object TestSystem extends TestSystem
  import TestSystem.messageEventBus
  import TestSystem.send

  implicit val db = Db()
  val discovery = new Discovery(FastCryptographicHash.hash)
  val sut = system.actorOf(DiscoveryActor.props(discovery))

  messageEventBus.subscribe(classOf[TestIncoming])
  import SerializedMessage.noChain

  "DiscoveryActor" should {

    "respond with peer page when no peers" in {
      TestSystem.messageEventBus.simulateNetworkMessage("DiscoveryActorSpec",
        MessageKeys.PeerPage,
        PeerPage(0,1000, ByteString("Random" + Random.nextInt()))
      )

      expectMsg(TestIncoming(PeerPage(0, 1000, ByteString())))
    }

    "store the peers it receives" in {

      val peers = (0 until 10) map { i => PeerPageResponse(s"node$i",

        new InetSocketAddress(
          InetAddress.getByName(s"127.0.0.$i"),
          80 + i),
        Capabilities(i.toByte))
      }

      TestSystem.messageEventBus.simulateNetworkMessage("DiscoveryActorSpec",
        MessageKeys.SeqPeerPageResponse,
        SeqPeerPageResponse(peers)
      )

      TestSystem.messageEventBus.simulateNetworkMessage("DiscoveryActorSpec",
        MessageKeys.PeerPage,
        PeerPage(0,1000, ByteString("Random" + Random.nextInt()))
      )

      val resp = receiveN(2)
      val pPage = TestUtils.extractAsType[PeerPage](resp.last)

      TestSystem.messageEventBus.simulateNetworkMessage("DiscoveryActorSpec",
        MessageKeys.PeerPage,
        PeerPage(0,1000, pPage.fingerprint)
      )

      expectNoMessage()

      awaitAssert {
        val asDiscovered = peers map (peer => DiscoveredNode(NodeId(peer.nodeId, peer.sockAddr), peer.capabilities.supportedChains))
        val (savedPeers, fingerprint) = discovery.query(0, 10)
        assert(savedPeers === asDiscovered)
      }
    }

  }

}
