package sss.asado.tools

import akka.actor.Status.{Failure => FutureFailure}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.BalanceLedger
import sss.asado.block.Synchronized
import sss.asado.chains.QuorumFollowersSyncedMonitor.BlockChainReady
import sss.asado.chains.SouthboundTxDistributorActor.SynchronizedConnection
import sss.asado.chains.TxWriterActor.{InternalAck, InternalCommit, InternalLedgerItem, InternalTxResult}
import sss.asado.contract.SaleOrReturnSecretEnc
import sss.asado.identityledger.Claim
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.network.MessageEventBus
import sss.asado.nodebuilder.BootstrapIdentity
import sss.asado.tools.SendTxSupport.SendTx
import sss.asado.wallet.Wallet

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object TestTransactionSender {

  def apply(bootstrapIdentities: List[BootstrapIdentity], wallet: Wallet)(implicit actorSystem: ActorSystem,
                                                          nodeIdentity: NodeIdentity,
            sendTx: SendTx,
            messageEventBus: MessageEventBus
           ): ActorRef = {
    actorSystem.actorOf(Props(classOf[TestTransactionSender], bootstrapIdentities, wallet, nodeIdentity, sendTx, messageEventBus))
  }
}

class TestTransactionSender(bootstrapIdentities: List[BootstrapIdentity], wallet: Wallet)(implicit nodeIdentity: NodeIdentity,
                           sendTx: SendTx,
                           messageEventBus: MessageEventBus) extends Actor with ActorLogging {

  messageEventBus.subscribe(classOf[Synchronized])

  private case object FireTx

  var running: Boolean = false

  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {

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
      Try(wallet.createTx(1)) match {
        case Failure(e) => log.warning(e.toString)
        case Success(tx) =>
          val le = LedgerItem(MessageKeys.BalanceLedger, tx.txId, tx.toBytes)
          sendTx(le) pipeTo self
      }


    case _ : Synchronized =>
      if(!running && nodeIdentity.id == "karl") {
        self ! FireTx
      }
  }
}
