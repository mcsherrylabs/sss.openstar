package messagesender

import akka.actor.{Actor, ActorLogging}
import sss.asado.MessageKeys
import sss.asado.balanceledger.{TxInput, TxOutput}
import sss.asado.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.asado.crypto.SeedBytes
import sss.asado.ledger._
import sss.asado.message.{FailureResponse, MessageEcryption, SavedAddressedMessage, SuccessResponse, _}
import sss.asado.network.NetworkController.SendToNodeId
import sss.asado.network.NetworkMessage
import sss.asado.nodebuilder.ClientNode
import sss.asado.util.ByteArrayEncodedStrOps._
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by alan on 7/13/16.
  */
case object TrySendMail

class MessageSendingActor(clientNode: ClientNode, inBox: MessageInBox, prefix:String, circularSeq:CircularSeq)
  extends Actor with ActorLogging {

  import clientNode._

  val chargePerMessage = 0
  val amountBuriedInMail = 10


  case class WalletUpdate(txId: TxId, debits: Seq[TxInput], credits: Seq[TxOutput])

  private var watchingMsgSpends: Map[String, WalletUpdate] = Map()

  private def createPaymentOuts(to: Identity, secret: Array[Byte]): Seq[TxOutput] = {
    Seq(
      TxOutput(chargePerMessage, SingleIdentityEnc(homeDomain.nodeId.id)),
      TxOutput(amountBuriedInMail, SaleOrReturnSecretEnc(nodeIdentity.id, to, secret, currentBlockHeight + 125))
    )
  }

  private lazy val makeSecret = SeedBytes(16)

  private var failFactor = 1

  override def receive: Receive = {

    case TrySendMail =>
      val to = s"${prefix}${circularSeq.next}"
      Try {
        val bal  = wallet.balance(currentBlockHeight)
        log.info(s"Wallet balance is $bal")
        val baseTx = wallet.createTx(chargePerMessage + amountBuriedInMail)

        val changeTxOut = baseTx.outs.take(1)
        val secret = makeSecret

        val accounts = identityService.accounts(to)
        if (accounts.nonEmpty) {
          val account = accounts.head.account
          val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(nodeIdentity, account.publicKey, "Hello", secret)
          val paymentOuts = createPaymentOuts(to, secret)
          val tx = wallet.appendOutputs(baseTx, paymentOuts: _*)
          val signedSTx = wallet.sign(tx, secret)
          val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)
          val m: SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toBytes, le.toBytes)
          watchingMsgSpends += le.txIdHexStr -> WalletUpdate(tx.txId, tx.ins, changeTxOut)

          wallet.markSpent(tx.ins)

          ncRef ! SendToNodeId(NetworkMessage(MessageKeys.MessageAddressed, m.addrMsg.toBytes), homeDomain.nodeId)
          log.info(s"Message attempt send to $to")

        } else throw new Error(s"$to has no account")

      } match {
        case Failure(e) =>
          log.info(s"Message sending to $to failed -> $e")
          context.system.scheduler.scheduleOnce(10  * failFactor seconds, self, TrySendMail)
          if(failFactor < 10) failFactor += 1
        case Success(_) => failFactor = 1
      }

    case NetworkMessage(MessageKeys.MessageResponse, bytes) =>
      bytes.toMessageResponse match {
        case SuccessResponse(txId) =>
          log.info(s"Message successfully sent!")
          watchingMsgSpends.get(txId.toBase64Str).map { walletUpdate =>
            wallet.update(walletUpdate.txId, walletUpdate.debits,walletUpdate.credits)
          }

        case FailureResponse(txId, info) =>
          log.info(s"Message rejected $info")
      }
      self ! TrySendMail
  }
}

