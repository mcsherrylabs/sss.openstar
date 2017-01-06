package sss.ui.nobu


import akka.actor.{Actor, ActorLogging, ActorRef}
import org.joda.time.DateTime
import scorex.crypto.signatures.SigningFunctions.PublicKey

import sss.asado.MessageKeys
import sss.asado.account.{NodeIdentity}
import sss.asado.balanceledger.TxOutput
import sss.asado.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.asado.ledger.{LedgerItem}
import sss.asado.message.{Identity, MessageEcryption, MessageInBox, SavedAddressedMessage}
import sss.asado.nodebuilder.ClientNode
import sss.asado.wallet.Wallet
import sss.ui.nobu.NobuNodeBridge.{WalletUpdate}
import sss.ui.nobu.ScheduledTransfersActor.DetailedMessageToSend

import scala.concurrent.duration.{FiniteDuration, HOURS, MINUTES}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Success, Try}

/**
  * Created by alan on 12/30/16.
  */
object ScheduledTransfersActor {
  case class DetailedMessageToSend(senderIdentity:NodeIdentity,
                                   userWallet: Wallet,
                                   to : Identity,
                                   account: PublicKey,
                                   text: String, amount: Int)
}

class ScheduledTransfersActor(nobuNode: ClientNode,clientEventActor: ActorRef) extends Actor with ActorLogging {

  import nobuNode.db

  private case object RunCronTransfers

  private lazy val minNumBlocksInWhichToClaim = nobuNode.conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  private lazy val chargePerMessage = nobuNode.conf.getInt("messagebox.chargePerMessage")
  private lazy val amountBuriedInMail = nobuNode.conf.getInt("messagebox.amountBuriedInMail")

  private val oneDayHours =  24
  private val every = HOURS

  context.system.scheduler.schedule(FiniteDuration(oneDayHours, every),
    FiniteDuration(oneDayHours, every),
    self, RunCronTransfers)


  private def createPaymentOuts(from: Identity, to: Identity, secret: Array[Byte], amount: Int): Seq[TxOutput] = {
    // Add 4 blocks to min to allow for the local ledger being some blocks behind the
    // up to date ledger.
    Seq(
      TxOutput(chargePerMessage, SingleIdentityEnc(nobuNode.homeDomain.nodeId.id)),
      TxOutput(amount, SaleOrReturnSecretEnc(from, to, secret,
        nobuNode.currentBlockHeight + minNumBlocksInWhichToClaim + 4))
    )
  }


  override def receive = {
    case DetailedMessageToSend(senderIdentity, userWallet, to, receiverPublicKey, text, amount) => Try {

      log.info("MessageToSend begins")
      val baseTx = userWallet.createTx(amount)
      val changeTxOut = baseTx.outs.take(1)
      val secret = new Array[Byte](8) //SeedBytes(16)
      Random.nextBytes(secret)

      val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(senderIdentity, receiverPublicKey, text, secret)
      val paymentOuts = createPaymentOuts(senderIdentity.id, to, secret, amount)
      val tx = userWallet.appendOutputs(baseTx, paymentOuts: _*)
      val signedSTx = userWallet.sign(tx, secret)
      val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)
      val inBox = MessageInBox(senderIdentity.id)
      val m: SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toMessagePayLoad, le.toBytes)
      // TODO watchingMsgSpends += le.txIdHexStr -> WalletUpdate(tx.txId, tx.ins, changeTxOut)
      log.info("MessageToSend finished, sending bag")
      clientEventActor ! Bag(userWallet, signedSTx, m, WalletUpdate(self, tx.txId, tx.ins, changeTxOut), senderIdentity.id)

    } match {
      case Failure(e) => log.error(e.toString)
      case Success(_) =>
    }

    case RunCronTransfers =>

      SchedulerPersistence().retrieve foreach {  schedule =>
          val s = Scheduler.toDetails(schedule)
          if (s.isDue(DateTime.now)) {
            UserSession(s.from) match {
              case None => log.error(s"${s.from} has no user session in memory now, they need to log in to unlock their keys")
              case Some(us) =>
                log.info("Sending now .... ")
                self ! DetailedMessageToSend(
                senderIdentity = us.nodeId,
                userWallet = us.userWallet,
                s.to,
                s.account,
                s.text,
                s.amount)
            }

          }
      }

  }
}
