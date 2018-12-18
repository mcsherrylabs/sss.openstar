package sss.openstar.chains

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.{Status, UniqueNodeIdentifier}
import sss.openstar.block.{NotSynchronized, Synchronized}
import sss.openstar.chains.LeaderElectionActor.{LeaderLost, LocalLeader}
import sss.openstar.common.block.BlockId
import sss.openstar.network.ConnectionLost

import sss.openstar.nodebuilder.{DecoderBuilder, MessageEventBusBuilder, RequireActorSystem}
import sss.openstar.peers.PeerManager.{Capabilities, PeerConnection}

import scala.language.postfixOps

class ChainSynchronizerSpec extends FlatSpec with Matchers {

  implicit private val chainId = 1.toByte
  import sss.openstar.TestUtils.actorSystem
  private val myNodeId = "myNodeId"

  private object TestSystem extends MessageEventBusBuilder
    with DecoderBuilder
    with RequireActorSystem {
    lazy implicit override val actorSystem: ActorSystem = sss.openstar.TestUtils.actorSystem

    def startSyncer(context: ActorContext): ActorRef = {

      class TestSyncer extends Actor {

        context.parent ! Synchronized(chainId, 0,0, "")

        override def receive: Receive = {
          case x =>
        }
      }

      context.actorOf(Props(new TestSyncer))
    }

    def synchronization(candidates: Set[UniqueNodeIdentifier], name: String) = ChainSynchronizer(
      candidates,
      myNodeId,
      startSyncer,
      () => BlockId(0,0),
      () => BlockId(0,0),
      name
    )
  }

  private val probe1 = TestProbe()
  private val observer1 = probe1.ref

  private val otherNodeId = "test"

  TestSystem.messageEventBus.subscribe(classOf[Synchronized])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[NotSynchronized])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[Status])(observer1)


  val syncWhenNoQuorumNeededWeAreOwner = TestSystem.synchronization(Set(myNodeId), "ChainsyncTest1")
  //val syncInTheNormalCase = TestSystem.synchronization(Set(myNodeId, otherNodeId), "ChainsyncTest2")

  "Synchronization" should "be synchronised when quorum is empty" in {
    syncWhenNoQuorumNeededWeAreOwner.startSync
    probe1.expectMsg(Synchronized(chainId, 0, 0, ""))
    syncWhenNoQuorumNeededWeAreOwner.shutdown
  }

  /*it should "be synchronized from a peer " in {
    syncInTheNormalCase.startSync
    TestSystem.messageEventBus.publish(PeerConnection("someNode", Capabilities(chainId)))
    probe1.expectMsg(Synchronized(chainId, 0, 0))
  }

  it should "be not synchronized when we lose connection" in {
    TestSystem.messageEventBus.publish(ConnectionLost("someNode"))
    probe1.expectMsg(NotSynchronized(chainId))
  }

  it should "be synchronized after becoming local leader" in {
    TestSystem.messageEventBus.publish(LocalLeader(chainId, "leader", 10, 10, Seq()))
    probe1.expectMsg(Synchronized(chainId, 10, 10))
  }

  it should "be not synchronized losing local leader ship" in {
    TestSystem.messageEventBus.publish(LeaderLost(chainId, "leader"))
    probe1.expectMsg(NotSynchronized(chainId))
  }*/

}
