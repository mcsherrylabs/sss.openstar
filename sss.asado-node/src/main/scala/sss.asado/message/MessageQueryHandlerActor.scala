package sss.asado.message

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
import sss.asado.MessageKeys._
import sss.asado.balanceledger._
import sss.asado.common.block._
import sss.asado.ledger._
import sss.asado.network.{MessageEventBus, _}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/8/16.
  */
class MessageQueryHandlerActor(messageRouter: MessageEventBus,
                               messagePaywall: MessagePaywall)(implicit db: Db)
    extends Actor
    with ActorLogging {

  messageRouter.subscribe(MessageKeys.MessageQuery)
  messageRouter.subscribe(MessageKeys.MessageAddressed)

  log.info("MessageQueryHandler actor has started ...")

  case class MessageTracker(sndr: ActorRef,
                            to: String,
                            index: Long,
                            resendNetMsg: SerializedMessage)

  private var messageSenders: Map[String, MessageTracker] = Map()

  override def receive: Receive = {
    case _ => ???
/*
    case SerializedMessage(MessageKeys.SignedTxAck, bytes) =>
      val bId = bytes.toBlockChainIdTx
      log.debug(s"FYI, got the message tx ack ${bId.height}, ${bId.blockTxId}")

    case SerializedMessage(MessageKeys.AckConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      messageSenders.get(bId.blockTxId.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).accept(tracker.index)) match {
          case Failure(e) =>
            tracker.sndr ! SerializedMessage(MessageKeys.MessageResponse,
                                          FailureResponse(
                                            bId.blockTxId.txId,
                                            e.getMessage.take(100)).toBytes)
          case Success(_) =>
            log.debug(s"sending ${tracker.sndr} the Success response")
            tracker.sndr ! SerializedMessage(
              MessageKeys.MessageResponse,
              SuccessResponse(bId.blockTxId.txId).toBytes)
        }
      }
      messageSenders -= bId.blockTxId.txId.toBase64Str

    case SerializedMessage(MessageKeys.TempNack, bytes) =>
      val txMsg = bytes.toTxMessage
      /*
      //TODO publish the actual event and have the TxWriter react to it.
      messageSenders.get(txMsg.txId.toBase64Str) foreach { tracker =>
        context.system.scheduler
          .scheduleOnce(5 seconds) {messageRouter.publish(tracker.resendNetMsg) }
      }*/

    case SerializedMessage(MessageKeys.SignedTxNack, bytes) =>
      val txMsg = bytes.toTxMessage
      messageSenders.get(txMsg.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).reject(tracker.index)) match {
          case Failure(e) =>
            tracker.sndr ! SerializedMessage(
              MessageKeys.MessageResponse,
              FailureResponse(txMsg.txId, e.getMessage.take(100)).toBytes)
          case Success(_) =>
            tracker.sndr ! SerializedMessage(
              MessageKeys.MessageResponse,
              FailureResponse(txMsg.txId, txMsg.msg).toBytes)
        }
      }
      messageSenders -= txMsg.txId.toBase64Str

    case SerializedMessage(MessageKeys.NackConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      messageSenders.get(bId.blockTxId.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).reject(tracker.index)) match {
          case Failure(e) =>
            tracker.sndr ! SerializedMessage(MessageKeys.MessageResponse,
                                          FailureResponse(
                                            bId.blockTxId.txId,
                                            e.getMessage.take(100)).toBytes)
          case Success(_) =>
            tracker.sndr ! SerializedMessage(
              MessageKeys.MessageResponse,
              FailureResponse(bId.blockTxId.txId,
                              "Failed to confirm Msg Tx on secondary").toBytes)
        }
      }
      messageSenders -= bId.blockTxId.txId.toBase64Str

    case SerializedMessage(_, MessageKeys.GenericErrorMessage, bytes) =>
      log.warning(new String(bytes, StandardCharsets.UTF_8))

    case IncomingSerializedMessage(
        nId: UniqueNodeIdentifier,
        SerializedMessage(_, MessageKeys.MessageQuery,
        bytes)) =>

      decode(MessageKeys.MessageQuery, bytes.toMessageQuery) {
        mq: MessageQuery =>
          val page = MessagePersist(nId).page(mq.lastIndex, mq.pageSize)
          val sndr = sender()
          page.foreach(m =>
            sndr ! SerializedMessage(MessageKeys.MessageMsg, m.toBytes))
          if (page.size == mq.pageSize)
            sndr ! SerializedMessage(MessageKeys.EndMessagePage, Array())
          else {
            sndr ! SerializedMessage(MessageKeys.EndMessageQuery, Array())
            // TODO add to push update list.
          }
      }

    case IncomingSerializedMessage(
        nId: UniqueNodeIdentifier,
        SerializedMessage(MessageKeys.MessageAddressed,
        bytes)) =>

      Try(bytes.toMessageAddressed) match {
        case Failure(e) =>
          sender ! SerializedMessage(
            MessageKeys.GenericErrorMessage,
            e.getMessage.take(100).getBytes(StandardCharsets.UTF_8))

        case Success(addrMsg) =>
          Try {
            val sTx = addrMsg.ledgerItem.txEntryBytes.toSignedTxEntry
            val toId: String = messagePaywall.validate(sTx.txEntryBytes.toTx)
            val index =
              MessagePersist(toId).pending(nId,
                                           addrMsg.msgPayload,
                                           addrMsg.ledgerItem.txEntryBytes)
            val netMsg =
              SerializedMessage(MessageKeys.SignedTx, addrMsg.ledgerItem.toBytes)
            messageSenders += (addrMsg.ledgerItem.txId.toBase64Str -> MessageTracker(
              sender(),
              toId,
              index,
              netMsg))
            /*
            TODO publish the actual event and have the TxWriter react to it.
            messageRouter.publish(netMsg)
            */

          } match {
            case Failure(e) =>
              log.error(e, "Unknown problem accepting incoming message")
              sender ! SerializedMessage(MessageKeys.MessageResponse,
                                      FailureResponse(
                                        addrMsg.ledgerItem.txId,
                                        e.getMessage.take(100)).toBytes)

            case Success(_) => // will send back success on Tx confirm
          }
      }
*/
  }
}
