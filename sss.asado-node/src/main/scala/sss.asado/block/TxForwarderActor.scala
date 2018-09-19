package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import com.twitter.util.SynchronizedLruMap
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
import sss.asado.MessageKeys.decode
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.common.block._
import sss.asado.ledger._
import sss.asado.network.{MessageEventBus, _}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.util.SeqSerializer

/**
  * Created by alan on 4/1/16.
  */
case class Forward(who: Connection)


class TxForwarderActor(messageRouter: MessageEventBus,
                       send: NetSendTo,
                       clientRefCacheSize: Int)
  extends Actor with ActorLogging with AsadoEventSubscribedActor {


  private case object StopAcceptingTxs
  private var txs = new SynchronizedLruMap[String, ActorRef](clientRefCacheSize)

  log.info("TxForwarder actor has started...")

  private def noForward: Receive = {

    // TODO REPLACE -> case RemoteLeaderEvent(con) => self ! Forward(con)

    case Forward(who) =>
      messageRouter.subscribe(MessageKeys.SignedTx)
      messageRouter.subscribe(MessageKeys.SeqSignedTx)
      messageRouter.subscribe(MessageKeys.SignedTxAck)
      messageRouter.subscribe(MessageKeys.SignedTxNack)
      messageRouter.subscribe(MessageKeys.AckConfirmTx)
      messageRouter.subscribe(MessageKeys.TempNack)

      messageRouter.subscribe(classOf[ConnectionLost])
      //context watch who.handlerRef

      context.become(forwardMode(who.nodeId))

  }

  private def forwardMode(leader: UniqueNodeIdentifier): Receive = {

    // TODO -> case NotReadyEvent =>  self ! StopAcceptingTxs

    case ConnectionLost(nodeId) if(leader == nodeId) =>
    // case Terminated(leaderRef) =>
      self ! StopAcceptingTxs


    case StopAcceptingTxs =>
      messageRouter.unsubscribe(MessageKeys.SignedTx)
      messageRouter.unsubscribe(MessageKeys.SeqSignedTx)
      messageRouter.unsubscribe(MessageKeys.SignedTxAck)
      messageRouter.unsubscribe(MessageKeys.SignedTxNack)
      messageRouter.unsubscribe(MessageKeys.AckConfirmTx)
      messageRouter.unsubscribe(MessageKeys.TempNack)
      context.become(noForward)


    case m @ SerializedMessage(_, MessageKeys.SignedTx, bytes) =>
      decode(MessageKeys.SignedTx, bytes.toLedgerItem) { stx =>
        txs +=  (stx.txId.toBase64Str -> sender())
        send(m, leader)
      }

    case m @ SerializedMessage(_, MessageKeys.SeqSignedTx, bytes) =>
      decode(MessageKeys.SeqSignedTx, SeqSerializer.fromBytes(bytes)) { seqStx =>
        seqStx foreach { stx =>
          val le: LedgerItem = stx.toLedgerItem
          txs += (le.txId.toBase64Str -> sender())
        }
        send(m, leader)
      }

    case m @ SerializedMessage(_, MessageKeys.SignedTxAck, bytes) =>
      decode(MessageKeys.SignedTxAck, bytes.toBlockChainTxId) { txAck =>
        txs.get(txAck.blockTxId.txId.toBase64Str) map (_ ! m)
      }

    case m @ SerializedMessage(_, MessageKeys.TempNack, bytes) =>
      decode(MessageKeys.TempNack, bytes.toTxMessage) { tempNack =>
        txs.get(tempNack.txId.toBase64Str) map (_ ! m)
      }

    case m @ SerializedMessage(_, MessageKeys.SignedTxNack, bytes) =>
      decode(MessageKeys.SignedTxNack, bytes.toTxMessage) { txNack =>
        txs.get(txNack.txId.toBase64Str) map (_ ! m)
        txs -= txNack.txId.toBase64Str
      }



    case m @ SerializedMessage(_, MessageKeys.AckConfirmTx, bytes) =>
      decode(MessageKeys.AckConfirmTx, bytes.toBlockChainTxId) { txAck =>
        txs.get(txAck.blockTxId.txId.toBase64Str) map (_ ! m)

      }
  }

  final override def receive = noForward

}
