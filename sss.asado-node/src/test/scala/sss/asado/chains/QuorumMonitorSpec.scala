package sss.asado.chains

import akka.actor.ActorSystem
import akka.testkit.TestProbe

import scala.concurrent.duration._
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.chains.QuorumMonitor.{NotQuorumCandidate, Quorum, QuorumLost}
import sss.asado.network.ConnectionLost
import sss.asado.nodebuilder._
import sss.asado.peers.PeerManager.{Capabilities, PeerConnection}
import sss.asado.peers.{PeerManager, PeerQuery}
import sss.asado.quorumledger.QuorumLedger.NewQuorumCandidates

import scala.language.postfixOps

class QuorumMonitorSpec extends FlatSpec with Matchers {

  private val chainId = 1.toByte
  import sss.asado.TestUtils.actorSystem

  private object TestSystem extends MessageEventBusBuilder
    with DecoderBuilder
    with RequirePeerQuery
    with RequireActorSystem {
    lazy implicit override val actorSystem: ActorSystem = sss.asado.TestUtils.actorSystem

    override val peerQuery: PeerQuery = new PeerQuery {
      override def addQuery(q: PeerManager.Query): Unit = ()
      override def removeQuery(q: PeerManager.Query): Unit = ()
    }

    val quorumMonitor = QuorumMonitor(messageEventBus, chainId, myNodeId, Set(), peerQuery)
  }

  private val probe1 = TestProbe()

  private val probe2 = TestProbe()
  private val observer1 = probe1.ref

  private val otherNodeId = "test"

  private val myNodeId = "myNodeId"

  TestSystem.messageEventBus.subscribe(classOf[Quorum])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[QuorumLost])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[NotQuorumCandidate])(observer1)

  /*"QuorumMonitor " should " have a quorum if none in membership " in {

    TestSystem.quorumMonitor.queryStatus
    probe1.expectMsg(Quorum(chainId, Set(), 0))
  }

  it should " have a quorum if only this node is a member" in {
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
