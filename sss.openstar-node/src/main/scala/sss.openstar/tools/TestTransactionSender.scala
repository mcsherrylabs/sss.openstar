package sss.openstar.tools

import akka.actor.Status.{Failure => FutureFailure}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import sss.openstar.account.NodeIdentity
import sss.openstar.{MessageKeys, UniqueNodeIdentifier}
import sss.openstar.balanceledger.{BalanceLedger, StandardTx, TxIndex, TxOutput}
import sss.openstar.block.Synchronized
import sss.openstar.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.openstar.chains.SouthboundTxDistributorActor.SynchronizedConnection
import sss.openstar.chains.TxWriterActor.{InternalAck, InternalCommit, InternalLedgerItem, InternalTxResult}
import sss.openstar.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.openstar.identityledger.Claim
import sss.openstar.ledger.{LedgerItem, SignedTxEntry}
import sss.openstar.network.{ConnectionLost, MessageEventBus}
import sss.openstar.nodebuilder.BootstrapIdentity
import sss.openstar.peers.PeerManager.PeerConnection
import sss.openstar.tools.SendTxSupport.SendTx
import sss.openstar.wallet.Wallet
import sss.openstar.wallet.Wallet.Payment

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object TestTransactionSender {

  def apply(bootstrapIdentities: List[BootstrapIdentity], wallet: Wallet)(implicit actorSystem: ActorSystem,
                                                          nodeIdentity: NodeIdentity,
            sendTx: SendTx,
            messageEventBus: MessageEventBus
           ): ActorRef = {

    actorSystem.actorOf(
      Props(classOf[TestTransactionSender],
        bootstrapIdentities,
        wallet,
        nodeIdentity,
        sendTx,
        messageEventBus)
    , "TestTransactionSender")

  }
}

class TestTransactionSender(bootstrapIdentities: List[BootstrapIdentity], wallet: Wallet)(implicit nodeIdentity: NodeIdentity,
                           sendTx: SendTx,
                           messageEventBus: MessageEventBus) extends Actor with ActorLogging {

  messageEventBus.subscribe(classOf[PeerConnection])
  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[Synchronized])

  private case object FireTx

  var running: Boolean = false
  var connected: Set[UniqueNodeIdentifier] = Set()

  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {

    case PeerConnection(nodeId, _) =>
      connected += nodeId

    case ConnectionLost(nId) =>
      connected -= nId


    case FutureFailure(e) =>
      running = false
      log.warning(e.toString)

    case com : InternalCommit =>
      log.info(s"Got commit: $com, going again")
      self ! FireTx

    case ack : InternalAck =>
      log.info(s"Got ack: $ack")

    case r : InternalTxResult =>
      log.info(r.toString)

    case FireTx =>
      running = true
      wallet.payAsync(self, Payment(nodeIdentity.id, 1))

    case _ : Synchronized =>
      if(nodeIdentity.id == "karl") {
        self ! FireTx
      }
  }
}
