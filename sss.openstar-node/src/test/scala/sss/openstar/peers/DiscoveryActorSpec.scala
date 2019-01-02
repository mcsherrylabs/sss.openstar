package sss.openstar.peers

import java.net.{InetAddress, InetSocketAddress}

import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, Matchers}
import sss.db.Db
import sss.openstar.{MessageKeys, Send}
import sss.openstar.network.{IncomingSerializedMessage, NetSend, NodeId, SerializedMessage}
import sss.openstar.network.TestMessageEventBusOps._
import sss.openstar.nodebuilder._
import sss.openstar.peers.DiscoveryActor.DiscoveredNode

import scala.language.postfixOps

class DiscoveryActorSpec extends FlatSpec with Matchers {

  private val chainId = 1.toByte

  import sss.openstar.TestUtils.actorSystem

  private val probe1 = TestProbe()

  private val probe2 = TestProbe()
  private val observer1 = probe1.ref

  private object TestSystem extends MessageEventBusBuilder
    with RequireActorSystem
    with DecoderBuilder
    with RequireNetSend {

    override implicit val send: Send = Send(ns)

    def ns: NetSend = (serMsg, targets) => {
      ()
    }
  }

  import TestSystem.messageEventBus
  import TestSystem.send

  implicit val db = Db()
  val discovery = new Discovery()
  val sut = actorSystem.actorOf(DiscoveryActor.props(discovery))

  "DiscoveryActor" should "store the peers it receives" in {

    val addr = InetAddress.getLocalHost.getAddress


    val peers = (0 to 10) map { i => PeerPageResponse(s"node$i",
      new InetSocketAddress(
        InetAddress.getByAddress(s"node$i", addr),
        80 + i),
      Capabilities(i.toByte))
    }

    import SerializedMessage.noChain
    val simulated = IncomingSerializedMessage("notImortant",
      SerializedMessage(MessageKeys.SeqPeerPageResponse, SeqPeerPageResponse(peers)))

    TestSystem.messageEventBus.simulateNetworkMessage(simulated)

    Thread.sleep(1000)
    val asDiscovered = peers map (peer => DiscoveredNode(NodeId(peer.nodeId, peer.sockAddr), peer.capabilities.supportedChains))
    val (savedPeers, fingerprint)= discovery.query(0, 10)
    assert(savedPeers === asDiscovered)
  }

  /*it should " have a quorum if only this node is a member" in {
    TestSystem.messageEventBus.publish(NewQuorumCandidates(chainId, Set(myNodeId)))
    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(Quorum(chainId, Set(), 0))
  }

  it should " not have a quorum if only another node is a member and not connected " in {
    TestSystem.messageEventBus.publish(NewQuorumCandidates(chainId, Set(myNodeId, otherNodeId)))
    probe1.expectMsg(QuorumLost(chainId))
    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(QuorumLost(chainId))
  }

  it should " indicate we are out of the quorum if we query when not a member " in {
    TestSystem.messageEventBus.publish(NewQuorumCandidates(chainId, Set(otherNodeId)))
    probe1.expectMsg(NotQuorumCandidate(chainId, myNodeId))
    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(NotQuorumCandidate(chainId, myNodeId))
  }

  it should " indicate we have a quorum if we query when members connected " in {
    TestSystem.messageEventBus.publish(NewQuorumCandidates(chainId, Set(otherNodeId, myNodeId)))
    TestSystem.messageEventBus.publish(PeerConnection(otherNodeId, Capabilities(chainId)))
    probe1.expectMsg(Quorum(chainId, Set(otherNodeId), 0))
    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(Quorum(chainId, Set(otherNodeId), 0))
  }

  it should " maintain a quorum if non member gets disconnected " in {
    TestSystem.messageEventBus.publish(ConnectionLost(otherNodeId + "random"))
    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(Quorum(chainId, Set(otherNodeId), 0))
  }

  it should " indicate we have lost quorum if member gets disconnected " in {
    TestSystem.messageEventBus.publish(ConnectionLost(otherNodeId))
    probe1.expectMsg(QuorumLost(chainId))
    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(QuorumLost(chainId))
  }*/
}
