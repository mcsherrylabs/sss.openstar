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
import sss.asado.ledger.LedgerItem
import sss.asado.message.{MessageEcryption, MessageInBox, SavedAddressedMessage}
import sss.asado.message._
import sss.asado.state.HomeDomain
import sss.asado.tools.SendTxSupport.SendTx
import sss.asado.wallet.Wallet
import sss.db.Db
import sss.ui.nobu.NobuNodeBridge.{Fail, MessageToSend, WalletUpdate}

import scala.util.{Failure, Random, Success, Try}

class SendMessage(currentBlockHeight: () => Long,
                 )
                 (implicit homeDomain: HomeDomain,
                  db:Db,
                  conf: Config,
                  sendTx: SendTx,
                  send: Send,
                  chainId: GlobalChainIdMask

) extends Logging {


  private lazy val minNumBlocksInWhichToClaim = conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  private lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")
  private lazy val amountBuriedInMail = conf.getInt("messagebox.amountBuriedInMail")

  def sendMessage: Receive = {
    case MessageToSend(to , account, text, amount, sender) =>

      Try {

        log.info("MessageToSend begins")
        val us = UserSession().get
        val userWallet: Wallet = us.userWallet
        val nodeIdentity = us.nodeId
        val inBox = MessageInBox(nodeIdentity.id)
        val baseTx = userWallet.createTx(amount)
        val changeTxOut = baseTx.outs.take(1)
        val secret = new Array[Byte](8) //SeedBytes(16)
        Random.nextBytes(secret)

        val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(nodeIdentity, account.publicKey, text, secret)
        val paymentOuts = Seq(
          TxOutput(chargePerMessage, SingleIdentityEnc(homeDomain.nodeId.id)),
          TxOutput(amount, SaleOrReturnSecretEnc(nodeIdentity.id, to, secret,
            currentBlockHeight() + minNumBlocksInWhichToClaim))
        )
        val tx = userWallet.appendOutputs(baseTx, paymentOuts : _*)
        val signedSTx = userWallet.sign(tx, secret)
        val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)
        val m : SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toMessagePayLoad, le.toBytes)

        send(MessageKeys.MessageAddressed, m.addrMsg, homeDomain.nodeId.id)

        // TODO watchingMsgSpends += le.txIdHexStr -> WalletUpdate(tx.txId, tx.ins, changeTxOut)
        log.info("MessageToSend finished, sending bag")
        //FIXME clientEventActor ! bag FIXME

      } match {
        case Failure(e) => sender ! Fail("Couldn't send the message")
        case Success(_) =>
      }


  }
}
