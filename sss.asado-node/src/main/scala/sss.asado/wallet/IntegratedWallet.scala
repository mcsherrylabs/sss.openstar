package sss.asado.wallet


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.block._
import sss.asado.ledger._
import sss.asado.network.NetworkMessage
import sss.asado.wallet.WalletPersistence.Lodgement

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by alan on 7/5/16.
  */
object IntegratedWallet {
  trait TxResult {
    val txIdentifier: Option[String]
  }

  case class TxSuccess(blockChainTxId: BlockChainTxId, txIndexes: TxIndex, txIdentifier: Option[String]) extends TxResult
  case class TxFailure(txMessage: TxMessage, txIdentifier: Option[String]) extends TxResult

  case class Payment(identity: String,
                      amount: Int,
                      txIdentifier: Option[String] = None,
                      numConfirms: Int = 1,
                      blockHeight: Long = 0)


  case class TxTracker(stx: SignedTxEntry,
                       payeeIndex: TxIndex,
                       numConfirms: Int,
                       result: Promise[TxResult],
                       txIdentifier: Option[String])
}

class IntegratedWallet(wallet: Wallet,
                       messageRouterActor: ActorRef)(implicit system: ActorSystem) extends Logging {


  import IntegratedWallet._

  private val inBetweenActor = system.actorOf(Props(WalletActorWrapper))


  object WalletActorWrapper extends Actor with ActorLogging {

    var paymentsInFlight: Map[String, TxTracker] = Map()

    override def receive: Receive = {

      case tt @ TxTracker(stx,_ ,_ , _, _) =>
        paymentsInFlight += stx.txId.asHexStr -> tt
        messageRouterActor ! NetworkMessage(MessageKeys.SignedTx,
          LedgerItem(MessageKeys.BalanceLedger, stx.txId, stx.toBytes).toBytes)

      case NetworkMessage(MessageKeys.SignedTxAck, bytes) => log.info(s"SignedTxAck")

      case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
        Try {
          log.info(s"PAYMENT ACK ")
          val bcTxId = bytes.toBlockChainIdTx
          val hexId = bcTxId.blockTxId.txId.asHexStr
          paymentsInFlight.get(hexId) match {
            case Some(txTracker) =>
              if(txTracker.numConfirms <= 1) {
                paymentsInFlight -= hexId
                txTracker.result.success(TxSuccess(bcTxId, txTracker.payeeIndex, txTracker.txIdentifier))
              } else {
                paymentsInFlight += hexId -> txTracker.copy(numConfirms = txTracker.numConfirms - 1)
              }
            case None => log.info("Got a confirm, for unlisted txid in wallet...")
          }
        } match {
          case Failure(e) => log.error(e, "Got payment Confirm, failed to respond to client")
          case Success(_) =>
        }

      case NetworkMessage(MessageKeys.NackConfirmTx, bytes) =>
          val blockChainTxId = bytes.toBlockChainIdTx
          log.warning(s"PAYMENT CONFIRM NACK $blockChainTxId")


      case NetworkMessage(MessageKeys.TempNack, bytes) =>
        Try {
          log.info(s"Temp NACK ")
          val txMsg = bytes.toTxMessage
          val hexId = txMsg.txId.asHexStr

          paymentsInFlight.get(hexId) match {
            case Some(txTracker) =>
              paymentsInFlight -= hexId
              context.system.scheduler.scheduleOnce(2 seconds, self, txTracker)
            case None => log.warning(s"Got a Temp Nack for unlisted txid in wallet! ${hexId}")
          }
        } match {
          case Failure(e) => log.error(e, s"Failed to decode an alledged ${MessageKeys.TempNack} message.")
          case Success(_) =>
        }

      case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
        Try {
          log.info(s"PAYMENT NACK ")
          val txMsg = bytes.toTxMessage
          val hexId = txMsg.txId.asHexStr

          paymentsInFlight.get(hexId) match {
            case Some(txTracker) =>
              paymentsInFlight -= hexId
              txTracker.result.success(TxFailure(txMsg, txTracker.txIdentifier))
            case None => log.warning(s"Got a Tx Nack, for unlisted txid in wallet! ${hexId}")
          }
        } match {
          case Failure(e) => log.error(e, s"Failed to decode an alledged ${MessageKeys.SignedTxNack} message.")
          case Success(_) =>
        }

    }
  }

  def balance = wallet.balance()

  def credit(lodgement: Lodgement): Unit = wallet.credit(lodgement)

  def payAsync(sendingActor: ActorRef, payment: Payment) = {
    log.info(s"Attempting to create a tx for ${payment.amount} with wallet balance ${wallet.balance()}")
    val tx = wallet.createTx(payment.amount)
    val enc = wallet.encumberToIdentity(payment.blockHeight, payment.identity)
    val finalTxUnsigned = wallet.appendOutputs(tx, TxOutput(payment.amount, enc))
    val txIndex = TxIndex(finalTxUnsigned.txId, finalTxUnsigned.outs.size - 1)
    val signedTx = wallet.sign(finalTxUnsigned)
    val p = Promise[TxResult]()
    p.future.map {
      case txs @ TxSuccess(blockChainTxId, _, _) =>
        wallet.update(blockChainTxId.blockTxId.txId, tx.ins, tx.outs, blockChainTxId.height)
        txs
    }.map(sendingActor ! _)

    inBetweenActor ! TxTracker(signedTx, txIndex, payment.numConfirms, p, payment.txIdentifier)
  }


  def pay(payment: Payment)(implicit timeout: Duration): TxResult = {
    val tx = wallet.createTx(payment.amount)
    val enc = wallet.encumberToIdentity(payment.blockHeight, payment.identity)
    val finalTxUnsigned = wallet.appendOutputs(tx, TxOutput(payment.amount, enc))
    val txIndex = TxIndex(finalTxUnsigned.txId, finalTxUnsigned.outs.size - 1)
    val signedTx = wallet.sign(finalTxUnsigned)
    val p = Promise[TxResult]()
    inBetweenActor ! TxTracker(signedTx, txIndex, payment.numConfirms, p, payment.txIdentifier)
    Await.result(p.future.map {
      case txs @ TxSuccess(blockChainTxId, _, _) =>
        wallet.update(blockChainTxId.blockTxId.txId, tx.ins, tx.outs, blockChainTxId.height)
        txs
    }, timeout)

  }
}
