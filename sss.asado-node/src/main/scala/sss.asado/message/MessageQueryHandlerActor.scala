package sss.asado.message

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.balanceledger._
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.TxWriterActor._
import sss.asado.ledger._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.{MessageEventBus, _}
import sss.db.Db

import concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/8/16.
  */
class MessageQueryHandlerActor(messagePaywall: MessagePaywall)(
  implicit db: Db,
  messageRouter: MessageEventBus,
  send: Send,
  chainId: GlobalChainIdMask
)
    extends Actor
    with ActorLogging {

  messageRouter.subscribe(MessageKeys.MessageQuery)
  messageRouter.subscribe(MessageKeys.MessageAddressed)

  log.info("MessageQueryHandler actor has started ...")

  case class MessageTracker(sendingId: UniqueNodeIdentifier,
                            to: String,
                            index: Long,
                            resendNetMsg: InternalLedgerItem)

  private var messageSenders: Map[String, MessageTracker] = Map()

  override def receive: Receive = {

    case IncomingMessage(`chainId`, MessageKeys.MessageAddressed, sendingId, addrMsg: AddressedMessage) =>

      Try {
        val sTx = addrMsg.ledgerItem.txEntryBytes.toSignedTxEntry
        val toId: String = messagePaywall.validate(sTx.txEntryBytes.toTx)
        val index =
          MessagePersist(toId).pending(addrMsg.from,
            addrMsg.msgPayload,
            addrMsg.ledgerItem.txEntryBytes)

        val ledgeItem = InternalLedgerItem(chainId, addrMsg.ledgerItem, Some(self))
        messageSenders += (addrMsg.ledgerItem.txId.toBase64Str -> MessageTracker(
          sendingId,
          toId,
          index,
          ledgeItem))

        messageRouter publish ledgeItem

      } match {
        case Failure(e) =>
          log.error(e, "Unknown problem accepting incoming message")
          send(MessageKeys.MessageResponse,
            FailureResponse(
              addrMsg.ledgerItem.txId,
              e.getMessage.take(100)),
            sendingId)

        case Success(_) => // will send back success on Tx confirm
      }

    case InternalAck(`chainId`, _) =>
      log.debug("Got ack, waiting for commit or Nack")

    case InternalCommit(`chainId`, blkTxId) =>

      messageSenders.get(blkTxId.blockTxId.txId.toBase64Str) match {
        case Some(tracker) =>
          Try(MessagePersist(tracker.to).accept(tracker.index)) match {
            case Failure(e) =>
              send(MessageKeys.MessageResponse,
                FailureResponse(
                  blkTxId.blockTxId.txId,
                  e.getMessage.take(100)), tracker.sendingId)
            case Success(_) =>
              log.debug(s"sending ${tracker.sendingId} the Success response")
              send(
                MessageKeys.MessageResponse,
                SuccessResponse(blkTxId.blockTxId.txId), tracker.sendingId)
          }
          messageSenders -= blkTxId.blockTxId.txId.toBase64Str
        case None =>
          log.error(s"No in memory record of ${blkTxId.blockTxId.txId.toBase64Str}, but it's committed.")
      }

    case InternalTempNack(`chainId`, txMsg) =>

      import context.dispatcher

      messageSenders.get(txMsg.txId.toBase64Str).foreach { tracker =>
        context.system.scheduler
          .scheduleOnce(5 seconds) {
            messageRouter.publish(tracker.resendNetMsg)
          }
      }

    case InternalNack(`chainId`, txMsg) =>

      messageSenders.get(txMsg.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).reject(tracker.index)) match {
          case Failure(e) =>
            send(
              MessageKeys.MessageResponse,
              FailureResponse(txMsg.txId, e.getMessage.take(100)),
              tracker.sendingId)
          case Success(_) =>
            send(
              MessageKeys.MessageResponse,
              FailureResponse(txMsg.txId, txMsg.msg),
              tracker.sendingId)
        }
      }
      messageSenders -= txMsg.txId.toBase64Str



    case IncomingMessage(`chainId`,
                            MessageKeys.MessageQuery,
                            nId,
                            MessageQuery(who, lastIndex, pageSize)) =>


      val page = MessagePersist(who).page(lastIndex, pageSize)

      page.foreach(m =>
        send(MessageKeys.MessageMsg, m, nId)
      )

      if (page.size == pageSize)
        send(MessageKeys.EndMessagePage, EndMessagePage(who), nId)
      else
        send(MessageKeys.EndMessageQuery, EndMessageQuery(who), nId)


  }
}
