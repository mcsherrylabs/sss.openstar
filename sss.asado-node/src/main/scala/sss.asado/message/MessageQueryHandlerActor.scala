package sss.asado.message

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.{MessageKeys}
import sss.asado.MessageKeys._
import sss.asado.network.MessageRouter.RegisterV2
import sss.asado.network.{IncomingNetworkMessage, NetworkMessage, NodeId}
import sss.asado.ledger._
import sss.asado.balanceledger._
import sss.asado.block._
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.db.Db
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by alan on 6/8/16.
  */

class MessageQueryHandlerActor(messageRouter: ActorRef,
                               messagePaywall: MessagePaywall)(implicit db: Db) extends Actor with ActorLogging {

  messageRouter ! RegisterV2(MessageKeys.MessageQuery)
  messageRouter ! RegisterV2(MessageKeys.MessageAddressed)

  log.info("MessageQueryHandler actor has started ...")

  case class MessageTracker(sndr: ActorRef, to: String, index: Long, resendNetMsg: NetworkMessage)

  private var messageSenders: Map[String, MessageTracker] = Map()

  override def receive: Receive = {

    case NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      val bId = bytes.toBlockChainIdTx
      log.debug(s"FYI, got the message tx ack ${bId.height}, ${bId.blockTxId}")


    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      messageSenders.get(bId.blockTxId.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).accept(tracker.index)) match {
          case Failure(e) => tracker.sndr ! NetworkMessage(MessageKeys.MessageResponse,
            FailureResponse(bId.blockTxId.txId, e.getMessage.take(100)).toBytes)
          case Success(_) =>
            log.debug(s"sending ${tracker.sndr} the Success respoinse")
            tracker.sndr ! NetworkMessage(MessageKeys.MessageResponse,SuccessResponse(bId.blockTxId.txId).toBytes)
        }
      }
      messageSenders -= bId.blockTxId.txId.toBase64Str

    case NetworkMessage(MessageKeys.TempNack, bytes) =>
      val txMsg = bytes.toTxMessage
      messageSenders.get(txMsg.txId.toBase64Str) foreach { tracker =>
        context.system.scheduler.scheduleOnce(5 seconds, messageRouter, tracker.resendNetMsg)
      }

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      val txMsg = bytes.toTxMessage
      messageSenders.get(txMsg.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).reject(tracker.index)) match {
          case Failure(e) => tracker.sndr ! NetworkMessage(MessageKeys.MessageResponse,
            FailureResponse(txMsg.txId, e.getMessage.take(100)).toBytes)
          case Success(_) => tracker.sndr ! NetworkMessage(MessageKeys.MessageResponse,
            FailureResponse(txMsg.txId, txMsg.msg).toBytes)
        }
      }
      messageSenders -= txMsg.txId.toBase64Str


    case NetworkMessage(MessageKeys.NackConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      messageSenders.get(bId.blockTxId.txId.toBase64Str) foreach { tracker =>
        Try(MessagePersist(tracker.to).reject(tracker.index)) match {
          case Failure(e) => tracker.sndr ! NetworkMessage(MessageKeys.MessageResponse,
            FailureResponse(bId.blockTxId.txId, e.getMessage.take(100)).toBytes)
          case Success(_) => tracker.sndr ! NetworkMessage(MessageKeys.MessageResponse,
            FailureResponse(bId.blockTxId.txId, "Failed to confirm Msg Tx on secondary").toBytes)
        }
      }
      messageSenders -= bId.blockTxId.txId.toBase64Str

    case NetworkMessage(MessageKeys.GenericErrorMessage, bytes) =>
      log.warning(new String(bytes, StandardCharsets.UTF_8))

    case IncomingNetworkMessage(nId: NodeId, NetworkMessage(MessageKeys.MessageQuery, bytes)) =>
      decode(MessageKeys.MessageQuery, bytes.toMessageQuery) { mq: MessageQuery =>
        val page = MessagePersist(nId.id).page(mq.lastIndex, mq.pageSize)
        val sndr = sender()
        page.foreach(m => sndr ! NetworkMessage(MessageKeys.MessageMsg, m.toBytes))
        if (page.size == mq.pageSize) sndr ! NetworkMessage(MessageKeys.EndMessagePage, Array())
        else {
          sndr ! NetworkMessage(MessageKeys.EndMessageQuery, Array())
          // TODO add to push update list.
        }
      }

    case IncomingNetworkMessage(nId: NodeId, NetworkMessage(MessageKeys.MessageAddressed, bytes)) =>
      Try(bytes.toMessageAddressed) match {
        case Failure(e) => sender ! NetworkMessage(MessageKeys.GenericErrorMessage,
          e.getMessage.take(100).getBytes(StandardCharsets.UTF_8))

        case Success(addrMsg) =>
          Try {
            val sTx = addrMsg.ledgerItem.txEntryBytes.toSignedTxEntry
            val toId: String = messagePaywall.validate(sTx.txEntryBytes.toTx)
            val index = MessagePersist(toId).pending(nId.id, addrMsg.msg, addrMsg.ledgerItem.toBytes)
            val netMsg = NetworkMessage(MessageKeys.SignedTx, addrMsg.ledgerItem.toBytes)
            messageSenders += (addrMsg.ledgerItem.txId.toBase64Str -> MessageTracker(sender(), toId, index, netMsg))
            messageRouter ! netMsg

          } match {
            case Failure(e) =>
              log.error(e, "Unknown problem accepting incoming message")
              sender ! NetworkMessage(MessageKeys.MessageResponse,
                FailureResponse(addrMsg.ledgerItem.txId, e.getMessage.take(100)).toBytes)

            case Success(_) => // will send back success on Tx confirm
          }
      }

  }
}
