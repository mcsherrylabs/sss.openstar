package sss.asado.chains

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.block.serialize.VoteLeaderSerializer
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.block.{FindLeader, Leader, VoteLeader}
import sss.asado.chains.LeaderElectionActor.{LeaderFound, MakeFindLeader, WeAreLeader}
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.network.TestMessageEventBusOps._
import sss.asado.network.{IncomingSerializedMessage, NetSend, SerializedMessage}
import sss.asado.nodebuilder.{DecoderBuilder, MessageEventBusBuilder, RequireActorSystem, RequireNetSend}
import sss.asado.peers.PeerManager.{Capabilities, PeerConnection}

import scala.language.postfixOps

class LeaderElectionActorSpec extends FlatSpec with Matchers {

  implicit private val chainId = 1.toByte
  import sss.asado.TestUtils.actorSystem
  private val myNodeId = "myNodeId"
  private val betterNode = "betterNode"

  private object TestSystem extends MessageEventBusBuilder
    with DecoderBuilder
    with RequireActorSystem
    with RequireNetSend {
    lazy implicit override val actorSystem: ActorSystem = sss.asado.TestUtils.actorSystem


    def createFind(nId: UniqueNodeIdentifier, sigIndx: Int): MakeFindLeader = () => {
      //FindLeader(height: Long, commitedTxIndex: Long, signatureIndex: Int, nodeId: UniqueNodeIdentifier)
      FindLeader(9, 5, sigIndx, nId)
    }

    def ns: NetSend = (serMsg, targets) => {
      serMsg match {
        //vote for better node if it's name is 'betterNode
        case SerializedMessage(chainId, MessageKeys.FindLeader, data) if(targets.contains(betterNode)) =>
          val m: IncomingSerializedMessage = IncomingSerializedMessage(targets.head,
            SerializedMessage(chainId, MessageKeys.Leader,
              Array())
          )
          messageEventBus.simulateNetworkMessage(m)

        //vote for local node if it's name is not 'betterNode'
        case SerializedMessage(chainId, MessageKeys.FindLeader, data) =>
          val m: IncomingSerializedMessage = IncomingSerializedMessage(targets.head,
            SerializedMessage(chainId, MessageKeys.VoteLeader,
              VoteLeader(myNodeId, 0, 0).toBytes)
          )
          messageEventBus.simulateNetworkMessage(m)

        //catch the leader msg
        case SerializedMessage(chainId, MessageKeys.Leader, _) =>
      }

      ()
    }

    override implicit val send: Send = Send(ns)

    LeaderElectionActor(myNodeId, createFind(myNodeId, 2))

  }

  private val probe1 = TestProbe()
  private val observer1 = probe1.ref

  private val otherNodeId = "test"

  TestSystem.messageEventBus.subscribe(classOf[LeaderFound])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[WeAreLeader])(observer1)

  "LeaderActor" should "produce leader if no quorum candidates exist " in {
    TestSystem.messageEventBus.publish(Quorum(chainId, Set(), 0))
    probe1.expectMsg(WeAreLeader(chainId, 0,0, Seq()))
  }

  it should " produce a leader (us) if another candidate with less credentials is connected" in {
    TestSystem.messageEventBus.publish(Quorum(chainId, Set(otherNodeId), 0))
    TestSystem.messageEventBus.publish(PeerConnection(otherNodeId, Capabilities(chainId)))
    probe1.expectMsg(WeAreLeader(chainId, 9,5, Seq(VoteLeader(myNodeId, 0,0)))) //TODO FIX this test to be meaningful.
  }

  it should " produce a leader (them) if another candidate with more credentials is connected after Quorum is lost " in {
    TestSystem.messageEventBus.publish(QuorumLost(chainId))
    TestSystem.messageEventBus.publish(Quorum(chainId, Set(betterNode, otherNodeId), 0))
    // assume otherNodeId is connected as QuorumMonitor takes care of that.
    TestSystem.messageEventBus.publish(PeerConnection(betterNode, Capabilities(chainId)))
    probe1.expectMsg(LeaderFound(chainId, betterNode, Set(betterNode, otherNodeId)))
  }

}
