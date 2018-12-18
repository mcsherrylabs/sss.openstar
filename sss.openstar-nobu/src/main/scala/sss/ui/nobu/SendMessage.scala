package sss.ui.nobu

import akka.actor.Actor
import akka.actor.Actor.Receive
import com.typesafe.config.Config
import com.vaadin.ui.{Notification, UI}
import sss.ancillary.Logging
import sss.openstar.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.openstar.balanceledger.TxOutput
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.openstar.crypto.SeedBytes
import sss.openstar.ledger.LedgerItem
import sss.openstar.message.{MessageEcryption, MessageInBox, SavedAddressedMessage}
import sss.openstar.message._
import sss.openstar.network.MessageEventBus
import sss.openstar.state.HomeDomain
import sss.openstar.tools.SendTxSupport.SendTx
import sss.openstar.wallet.Wallet
import sss.db.Db
import sss.openstar.account.PublicKeyAccount
import sss.ui.nobu.SendMessage.MessageToSend
import sss.ui.nobu.UIActor.TrackMsgTxId

import scala.util.{Failure, Random, Success, Try}

object SendMessage {

  case class MessageToSend(
                            from: UniqueNodeIdentifier,
                           to : UniqueNodeIdentifier,
                           account: PublicKeyAccount,
                           text: String,
                           amount: Int,
                          )(implicit val ui: UI)
}

class SendMessage(currentBlockHeight: () => Long,
                  conf: Config
                 )
                 (implicit homeDomain: HomeDomain,
                  db:Db,
                  messageEventBus: MessageEventBus,
                  send: Send,
                  chainId: GlobalChainIdMask
) extends Logging with BlockingWorkerUIHelper {


  private lazy val minNumBlocksInWhichToClaim = conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  private lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")
  private lazy val amountBuriedInMail = conf.getInt("messagebox.amountBuriedInMail")

  def sendMessage: Receive = {
    case m@MessageToSend(from, to, account, text, amount) =>
      import m.ui
      Try {

        log.info("Message To Send begins")
        UserSession(from) match {
          case Some(us) =>

            val userWallet: Wallet = us.userWallet
            val nodeIdentity = us.nodeId
            val inBox = MessageInBox(nodeIdentity.id)
            val baseTx = userWallet.createTx(amount + chargePerMessage)
            val changeTxOut = baseTx.outs.take(1)
            val secret = SeedBytes.secureSeed(16)


            val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(nodeIdentity, account.publicKey, text, secret)
            val paymentOuts = Seq(
              TxOutput(chargePerMessage, SingleIdentityEnc(homeDomain.nodeId.id)),
              TxOutput(amount, SaleOrReturnSecretEnc(nodeIdentity.id, to, secret,
                currentBlockHeight() + minNumBlocksInWhichToClaim))
            )
            val tx = userWallet.appendOutputs(baseTx, paymentOuts: _*)
            val signedSTx = userWallet.sign(tx, secret)
            val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)
            val m: SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toMessagePayLoad, le.toBytes)

            messageEventBus publish TrackMsgTxId(ui.getSession.getSession.getId, m.addrMsg.ledgerItem.txIdHexStr)
            send(MessageKeys.MessageAddressed, m.addrMsg, homeDomain.nodeId.id)

          case None =>
            show("No user session, log out and log in again.", Notification.Type.ASSISTIVE_NOTIFICATION)
        }
      } match {
        case Failure(e) =>
          log.error(e.toString)
          show("Couldn't send the message", Notification.Type.WARNING_MESSAGE)
        case Success(_) =>
      }


  }
}
