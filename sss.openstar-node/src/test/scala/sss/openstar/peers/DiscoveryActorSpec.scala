package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers, WordSpecLike}
import sss.ancillary.Logging
import sss.db.Db
import sss.openstar.TestUtils.TestIncoming
import sss.openstar.{MessageKeys, Send, TestUtils}
import sss.openstar.network.{NetSend, NodeId, SerializedMessage}
import sss.openstar.network.TestMessageEventBusOps._
import sss.openstar.nodebuilder._
import sss.openstar.peers.Discovery.DiscoveredNode
import sss.openstar.util.hash.FastCryptographicHash

import scala.language.postfixOps
import scala.util.Random

class DiscoveryActorSpec extends TestKit(TestUtils.actorSystem) with WordSpecLike with Matchers with ImplicitSender {

  private val chainId = 1.toByte

  private val probe1 = TestProbe()

  private val probe2 = TestProbe()
  private val observer1 = probe1.ref

  private object TestSystem extends MessageEventBusBuilder
    with RequireActorSystem
    with DecoderBuilder
    with RequireNetSend
    with Logging {

    val receivedNetworkMessages = new AtomicReference[Seq[Any]](Seq.empty)
    //val receivedNetworkMessagesByReceiver = new AtomicReference[Map[String, Seq[Any]]](Map().withDefaultValue(Seq.empty))

    override implicit val send: Send = Send(ns)

    def ns: NetSend = (serMsg, targets) => {
      decoder(serMsg.msgCode) match {
        case Some(info) =>
          val msg = info.fromBytes(serMsg.data)
          //val incomingMsg = IncomingMessage(serMsg.chainId, serMsg.msgCode, "SUT", msg)

          receivedNetworkMessages.getAndUpdate({ msgs: Seq[_] =>
            msgs :+ msg
          })

          messageEventBus publish TestIncoming(msg)
        case None => log.warn(s"No decoding info found for ${serMsg.msgCode}")
      }
      ()
    }
  }

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
