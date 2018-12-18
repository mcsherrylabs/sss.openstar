package sss.openstar.message

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.openstar.balanceledger.{StandardTx, TxIndex, TxInput, TxOutput}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.chains.TxWriterActor.{InternalCommit, InternalLedgerItem, InternalNack}
import sss.openstar.contract.SaleSecretDec
import sss.openstar.balanceledger._
import sss.openstar.common.block.{BlockChainTxId, TxMessage}
import sss.openstar.identityledger.IdentityServiceQuery
import sss.openstar.ledger._
import sss.openstar.message.MessageDownloadActor._
import sss.openstar.message.MessageEcryption.EncryptedMessage
import sss.openstar.{MessageKeys, OpenstarEvent, Send, UniqueNodeIdentifier}
import sss.openstar.network.MessageEventBus
import sss.openstar.network.MessageEventBus.IncomingMessage
import sss.openstar.state.HomeDomain
import sss.openstar.wallet.Wallet
import sss.db.Db
import sss.openstar.account.NodeIdentity
import sss.openstar.util.ByteArrayEncodedStrOps._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/8/16.
  */


object MessageDownloadActor {

  type ValidateBounty = (Long, UniqueNodeIdentifier) => Boolean
  type AtomicNodeActorRefMap = AtomicReference[Map[UniqueNodeIdentifier, ActorRef]]

  case object CheckForMessages
  case object ForceCheckForMessages
  case class NewInBoxMessage(msg: Message) extends OpenstarEvent

  private val onePerNodeId: AtomicNodeActorRefMap = new AtomicReference(Map.empty)

  def apply(validateBounty: ValidateBounty,
            who: NodeIdentity,
            userWallet: Wallet,
            homeDomain: HomeDomain)(implicit actorSystem: ActorSystem,
                                    db: Db,
                                    messageEventBus: MessageEventBus,
                                    identityServiceQuery: IdentityServiceQuery,
                                    send: Send,
                                    chainId: GlobalChainIdMask): ActorRef = {
    val nodeId = who.id

    lazy val makeMessageDownloadActor: ActorRef = actorSystem.actorOf(
      Props(classOf[MessageDownloadActor],
        validateBounty,
        onePerNodeId,
        who,
        userWallet,
        homeDomain,
        db,
        messageEventBus,
        identityServiceQuery,
        send,
        chainId)
    )

    onePerNodeId.updateAndGet( all => {
      if(all contains(nodeId)) all
      else all + (nodeId -> makeMessageDownloadActor)
    })(nodeId)

  }
}
class MessageDownloadActor(validateBounty: ValidateBounty,
                           refMap: AtomicNodeActorRefMap,
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


  override def postStop(): Unit = {

    refMap.getAndUpdate( refs =>
      refs - userId.id
    )

  }

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
