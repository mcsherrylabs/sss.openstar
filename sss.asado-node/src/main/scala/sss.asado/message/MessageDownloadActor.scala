package sss.asado.message

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{StandardTx, TxIndex, TxInput, TxOutput}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.TxWriterActor.{InternalCommit, InternalLedgerItem, InternalNack}
import sss.asado.contract.SaleSecretDec
import sss.asado.balanceledger._
import sss.asado.common.block.{BlockChainTxId, TxMessage}
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger._
import sss.asado.message.MessageDownloadActor.{CheckForMessages, ForceCheckForMessages, NewInBoxMessage, ValidateBounty}
import sss.asado.message.MessageEcryption.EncryptedMessage
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.network.MessageEventBus
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.state.HomeDomain
import sss.asado.wallet.Wallet
import sss.db.Db
import sss.asado.util.ByteArrayEncodedStrOps._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/8/16.
  */


object MessageDownloadActor {

  type ValidateBounty = (Long, UniqueNodeIdentifier) => Boolean

  case object CheckForMessages
  case object ForceCheckForMessages
  case class NewInBoxMessage(msg: Message) extends AsadoEvent

  def apply(validateBounty: ValidateBounty,
            who: NodeIdentity,
            userWallet: Wallet,
            homeDomain: HomeDomain)(implicit actorSystem: ActorSystem,
                                    db: Db,
                                    messageEventBus: MessageEventBus,
                                    identityServiceQuery: IdentityServiceQuery,
                                    send: Send,
                                    chainId: GlobalChainIdMask): ActorRef = {
    actorSystem.actorOf(
      Props(classOf[MessageDownloadActor],
        validateBounty,
        who,
        userWallet,
        homeDomain,
        db,
        messageEventBus,
        identityServiceQuery,
        send,
        chainId)
    )
  }
}
class MessageDownloadActor(validateBounty: ValidateBounty,
                           userId: NodeIdentity,
                           userWallet: Wallet,
                           homeDomain: HomeDomain,
                           )(implicit db: Db,
                             messageEventBus: MessageEventBus,
                             identityServiceQuery: IdentityServiceQuery,
                             send: Send,
                             chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging {

  private var tracker: Map[String, Message] = Map()

  messageEventBus.subscribe(MessageKeys.MessageMsg)
  messageEventBus.subscribe(MessageKeys.EndMessageQuery)
  messageEventBus.subscribe(MessageKeys.EndMessagePage)

  log.info("MessageDownload actor has started...")

  private val who = userId.id
  private val inBox = MessageInBox(who)

  private var isQuiet = true

  def createQuery: MessageQuery = MessageQuery(who, inBox.maxInIndex, 25)

  override def receive: Receive = {

    case ForceCheckForMessages =>
      isQuiet = true
      self ! CheckForMessages

    case CheckForMessages =>
      if (isQuiet) {
        send(
            MessageKeys.MessageQuery,
            createQuery,
          homeDomain.nodeId.id)

        isQuiet = false
      }

    case IncomingMessage(_, _, _, EndMessagePage(`who`)) =>
      isQuiet = true
      self ! CheckForMessages

    case IncomingMessage(_, _, _, EndMessageQuery(`who`)) =>
      isQuiet = true
      context.system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS),
                                            self,
                                            CheckForMessages)


    case IncomingMessage(_, _, _, msg: Message) if(msg.to == who) =>

      val enc = MessagePayloadDecoder.decode(msg.msgPayload).asInstanceOf[EncryptedMessage]
      identityServiceQuery.accountOpt(msg.from) match {
        case None         => log.error("Message from unknown identity {}, cannot decrypt", msg.from)
        case Some(sender) =>
          val msgText = enc.decrypt(userId, sender.publicKey)
          val sTx = msg.tx.toSignedTxEntry
          val tx = sTx.txEntryBytes.toTx
          val adjustedIndex = tx.outs.size - 1
          val ourBounty = tx.outs(adjustedIndex)
          if(validateBounty(ourBounty.amount, msg.from)) {
            val inIndex = TxIndex(tx.txId, adjustedIndex)
            val in = TxInput(inIndex, ourBounty.amount, SaleSecretDec)
            val out = TxOutput(ourBounty.amount, userWallet.encumberToIdentity())
            val newTx = StandardTx(Seq(in), Seq(out))
            val sig = SaleSecretDec.createUnlockingSignature(newTx.txId, userId.tag, userId.sign, msgText.secret)
            val signedTx = SignedTxEntry(newTx.toBytes, Seq(sig))
            val le = LedgerItem(MessageKeys.BalanceLedger, signedTx.txId, signedTx.toBytes)
            tracker += le.txIdHexStr -> msg
            messageEventBus publish InternalLedgerItem(chainId, le, Some(self))
          } else {
            log.error("Message bounty failed to validate.", ourBounty.amount)
          }
      }

    case InternalNack(`chainId`, txMsg: TxMessage) =>
      val trackingId = txMsg.txId.toBase64Str
      log.error("Got a network Nack for {}, error - {} ", trackingId, txMsg.msg)
      tracker.get(trackingId) match {
        case None => log.error("Got a Nack for {} but we're not tracking that message", trackingId)
        case Some(msg) =>
          tracker -= trackingId
          inBox.addJunk(msg)
      }


    case InternalCommit(`chainId`, blTxId: BlockChainTxId) =>
      val trackingId = blTxId.blockTxId.txId.toBase64Str
      tracker.get(trackingId) match {
        case None => log.error("Got a confirm for {} but we're not tracking that message", trackingId)
        case Some(msg) =>
          tracker -= trackingId
          inBox.addNew(msg)
          messageEventBus publish(NewInBoxMessage(msg))
      }

  }


}
