package sss.openstar.chains

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, Matchers}
import sss.openstar.block.serialize.VoteLeaderSerializer
import sss.openstar.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.openstar.block.{FindLeader, GetLatestCommittedBlockId, Leader, VoteLeader}
import sss.openstar.chains.LeaderElectionActor.{LocalLeader, MakeFindLeader, RemoteLeader}
import sss.openstar.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.openstar.common.block.BlockId
import sss.openstar.network.TestMessageEventBusOps._
import sss.openstar.network.{IncomingSerializedMessage, NetSend, SerializedMessage}
import sss.openstar.nodebuilder.{DecoderBuilder, MessageEventBusBuilder, RequireActorSystem, RequireNetSend}


import scala.language.postfixOps

class LeaderElectionActorSpec extends FlatSpec with Matchers {

  implicit private val chainId = 1.toByte
  import sss.openstar.TestUtils.actorSystem
  private val myNodeId = "myNodeId"
  private val betterNode = "betterNode"

  private object TestSystem extends MessageEventBusBuilder
    with DecoderBuilder
    with RequireActorSystem
    with RequireNetSend {
    lazy implicit override val actorSystem: ActorSystem = sss.openstar.TestUtils.actorSystem


    def latestBlockId: GetLatestCommittedBlockId = () => BlockId(9,5)

    def createFind(nId: UniqueNodeIdentifier, sigIndx: Int): MakeFindLeader = () => {
      //FindLeader(height: Long, commitedTxIndex: Long, signatureIndex: Int, nodeId: UniqueNodeIdentifier)
      FindLeader(9, 5, 4, sigIndx, nId)
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

    LeaderElectionActor(myNodeId, createFind(myNodeId, 2), latestBlockId)

  }

  private val probe1 = TestProbe()
  private val observer1 = probe1.ref

  private val otherNodeId = "test"

  TestSystem.messageEventBus.subscribe(classOf[RemoteLeader])(observer1)
  TestSystem.messageEventBus.subscribe(classOf[LocalLeader])(observer1)

  /*"LeaderActor" should "produce leader if no quorum candidates exist " in {
    TestSystem.messageEventBus.publish(Quorum(chainId, Set(), 0))
    probe1.expectMsg(LocalLeader(chainId, myNodeId, 9,5, Seq()))
  }

  it should " produce a leader (us) if another candidate with less credentials is connected" in {
    TestSystem.messageEventBus.publish(Quorum(chainId, Set(otherNodeId), 0))
    TestSystem.messageEventBus.publish(PeerConnection(otherNodeId, Capabilities(chainId)))
    probe1.expectMsg(LocalLeader(chainId, myNodeId, 9,5, Seq(VoteLeader(myNodeId, 0,0)))) //TODO FIX this test to be meaningful.
  }

  it should " produce a leader (them) if another candidate with more credentials is connected after Quorum is lost " in {
    TestSystem.messageEventBus.publish(QuorumLost(chainId))
    TestSystem.messageEventBus.publish(Quorum(chainId, Set(betterNode, otherNodeId), 0))
    // assume otherNodeId is connected as QuorumMonitor takes care of that.
    TestSystem.messageEventBus.publish(PeerConnection(betterNode, Capabilities(chainId)))
    probe1.expectMsg(RemoteLeader(chainId, betterNode, Set(betterNode, otherNodeId)))
  }
  */

}
