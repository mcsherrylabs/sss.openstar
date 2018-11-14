package sss.ui.nobu

import akka.actor.Actor
import akka.actor.Actor.Receive
import com.typesafe.config.Config
import com.vaadin.ui.Notification
import sss.ancillary.Logging
import sss.asado.{MessageKeys, Send}
import sss.asado.account.PublicKeyAccount
import sss.asado.balanceledger.TxOutput
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.asado.crypto.SeedBytes
import sss.asado.ledger.LedgerItem
import sss.asado.message.{MessageEcryption, MessageInBox, SavedAddressedMessage}
import sss.asado.message._
import sss.asado.state.HomeDomain
import sss.asado.tools.SendTxSupport.SendTx
import sss.asado.wallet.Wallet
import sss.db.Db
import sss.ui.nobu.NobuNodeBridge.{Fail, MessageToSend}

import scala.util.{Failure, Random, Success, Try}

class SendMessage(currentBlockHeight: () => Long,
                  conf: Config
                 )
                 (implicit homeDomain: HomeDomain,
                  db:Db,
                  sendTx: SendTx,
                  send: Send,
                  chainId: GlobalChainIdMask
) extends Logging {


  private lazy val minNumBlocksInWhichToClaim = conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  private lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")
  private lazy val amountBuriedInMail = conf.getInt("messagebox.amountBuriedInMail")

  def sendMessage: Receive = {
    case MessageToSend(from, to, account, text, amount, sender) =>

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

            send(MessageKeys.MessageAddressed, m.addrMsg, homeDomain.nodeId.id)

          case None =>
            sender ! Fail("No user session, log out and log in again.")
        }
      } match {
        case Failure(e) =>
          log.error(e.toString)
          sender ! Fail("Couldn't send the message")
        case Success(_) =>
      }


  }
}
