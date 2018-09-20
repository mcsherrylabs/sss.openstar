package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.{Status, UniqueNodeIdentifier}
import sss.asado.block.Synchronized
import sss.asado.chains.ChainSynchronizer.NotSynchronized


import scala.concurrent.duration._
import sss.asado.nodebuilder.{DecoderBuilder, MessageEventBusBuilder, RequireActorSystem}
import sss.asado.peers.PeerManager.{Capabilities, PeerConnection}

import scala.language.postfixOps

class ChainSynchronizerSpec extends FlatSpec with Matchers {

  implicit private val chainId = 1.toByte
  import sss.asado.TestUtils.actorSystem
  private val myNodeId = "myNodeId"

  private object TestSystem extends MessageEventBusBuilder
    with DecoderBuilder
    with RequireActorSystem {
    lazy implicit override val actorSystem: ActorSystem = sss.asado.TestUtils.actorSystem

    def startSyncer(context: ActorContext, peerConnection: PeerConnection): Unit = {

      class TestSyncer extends Actor {

        if(peerConnection.nodeId == "synced") {
          context.parent ! Synchronized(chainId, 0,0)
        } else {
          context.parent ! NotSynchronized(chainId, peerConnection.nodeId)
        }

        context stop self
        override def receive: Receive = {
          case x =>
        }
      }

      context.actorOf(Props(new TestSyncer))
    }

    def synchronization(candidates: Set[UniqueNodeIdentifier]) = ChainSynchronizer(candidates, myNodeId, startSyncer)
  }

  private val probe1 = TestProbe()
  private val observer1 = probe1.ref

  private val otherNodeId = "test"

  TestSystem.messageEventBus.subscribe(classOf[Synchronized])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[NotSynchronized])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[Status])(observer1)

  val syncWhenNoQuorumNeeded = TestSystem.synchronization(Set())
  val syncWhenNoQuorumNeededWeAreOwner = TestSystem.synchronization(Set(myNodeId))
  val syncInTheNormalCase = TestSystem.synchronization(Set(myNodeId, otherNodeId))

  "Synchronization" should "be synchronised when quorum is empty" in {
    syncWhenNoQuorumNeeded.queryStatus
    probe1.expectMsg(Status(Some(Synchronized(chainId, 0, 0))))
  }

  it should "be synchronised when quorum is only this node" in {
    syncWhenNoQuorumNeededWeAreOwner.queryStatus
    probe1.expectMsg(Status(Some(Synchronized(chainId, 0, 0))))
  }

  it should "be not synchronised when quorum contains other unconnected nodes" in {
    syncInTheNormalCase.queryStatus
    probe1.expectMsg(Status(None))
  }

  it should "be not synchronised after connecting to not synchronised peer and downloading partial chain" in {

    TestSystem.messageEventBus.publish(PeerConnection("notsynced", Capabilities(chainId)))
    syncInTheNormalCase.queryStatus
    probe1.expectMsg(Status(None))
  }

  it should "be synchronised after connecting to a second peer should the first fail" in {

    TestSystem.messageEventBus.publish(PeerConnection("failing", Capabilities(chainId)))
    TestSystem.messageEventBus.publish(PeerConnection("synced", Capabilities(chainId)))
    probe1.expectMsg(Synchronized(chainId, 0, 0))
  }


}
