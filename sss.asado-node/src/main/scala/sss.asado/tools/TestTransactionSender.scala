package sss.asado.tools

import akka.actor.Status.{Failure => FutureFailure}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{BalanceLedger, StandardTx, TxIndex, TxOutput}
import sss.asado.block.Synchronized
import sss.asado.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.asado.chains.SouthboundTxDistributorActor.SynchronizedConnection
import sss.asado.chains.TxWriterActor.{InternalAck, InternalCommit, InternalLedgerItem, InternalTxResult}
import sss.asado.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.asado.identityledger.Claim
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.network.{ConnectionLost, MessageEventBus}
import sss.asado.nodebuilder.BootstrapIdentity
import sss.asado.peers.PeerManager.PeerConnection
import sss.asado.tools.SendTxSupport.SendTx
import sss.asado.wallet.Wallet
import sss.asado.wallet.Wallet.Payment

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
      if(!running && nodeIdentity.id == "karl") {
        self ! FireTx
      }
  }
}
