package sss.asado.chains


import akka.testkit.{TestActorRef, TestProbe}
import org.scalatest.{FlatSpec, Matchers}
import sss.asado.block.{Block, Synchronized}
import sss.asado.chains.ChainSynchronizer.NotSynchronized
import sss.asado.{TestSystem2, _}
import sss.asado.nodebuilder._
import sss.asado.peers.PeerManager.{Capabilities, PeerConnection}

import scala.language.postfixOps


class ChainDownloadRequestActorSpec extends FlatSpec with Matchers {

  private val betterNode = "betterNode"

  object t1 extends TestSystem1
    with NodeConfigBuilder
    with NodeIdentityBuilder
    with BalanceLedgerBuilder
    with IdentityServiceBuilder
    with RequirePhrase
    with BlockChainBuilder
    with BootstrapIdentitiesBuilder
    with NetworkInterfaceBuilder
    with HandshakeGeneratorBuilder
    with NetworkControllerBuilder
    with ChainBuilder {

    override val phrase: Option[String] = Some("password")


    ChainDownloadResponseActor(nodeConfig.blockChainSettings.maxSignatures, bc)

    val peerConnection = PeerConnection("peer", Capabilities(globalChainId))

    lazy override val testSystem2 = t2

  }

  import t1.actorSystem
  private val probe1 = TestProbe()
  private val observer1 = probe1.ref

  object t2 extends TestSystem2
    with NodeConfigBuilder
    with NodeIdentityBuilder
    with BalanceLedgerBuilder
    with IdentityServiceBuilder
    with RequirePhrase
    with BlockChainBuilder
    with BootstrapIdentitiesBuilder
    with NetworkInterfaceBuilder
    with HandshakeGeneratorBuilder
    with NetworkControllerBuilder
    with ChainBuilder {

    lazy override val testSystem1 = t1

    val peerConnection = PeerConnection(t1.nodeId, Capabilities(globalChainId))

    import chain.ledgers
    val reqProps = ChainDownloadRequestActor.props(
      peerConnection,
      nodeIdentity,
      bc)


    TestActorRef(reqProps.p, observer1, "ChildActor")
  }


  private val otherNodeId = "test"

  t2.messageEventBus.subscribe(classOf[NotSynchronized])(observer1)
  t2.messageEventBus.subscribe(classOf[Synchronized])(observer1)

  "ChainDownloaderActor" should " emit NotSynchronized after downloading 3 blocks" in {

    TestUtils.addOneBlock(t1.balanceLedger, t1.nodeIdentity, t1.bc)
    TestUtils.addOneBlock(t1.balanceLedger, t1.nodeIdentity, t1.bc)
    TestUtils.addOneBlock(t1.balanceLedger, t1.nodeIdentity, t1.bc)

    probe1.expectMsg(NotSynchronized(t2.globalChainId, t2.peerConnection.nodeId))
    val lastHeader = t2.bc.lastBlockHeader
    assert(lastHeader.height == 1, "Should have downloaded 3 blocks (plus genesis was always there)")
    assert(lastHeader.numTxs == 0, "Should have downloaded 10 txs' in last block")

  }

  ignore should " emit Synchronized after downloading 3 blocks" in {

    TestUtils.addOneBlock(t1.balanceLedger, t1.nodeIdentity, t1.bc)
    t1.messageEventBus.publish(Synchronized(t1.globalChainId, 5, 10))

    probe1.expectMsg(Synchronized(t2.globalChainId, 6, 0))
    val lastHeader = t2.bc.lastBlockHeader
    assert(lastHeader.height == 5, "Should have downloaded another block")
    assert(lastHeader.numTxs == 10, "Should have downloaded 10 txs' in last block")

  }

}
