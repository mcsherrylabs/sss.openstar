package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import com.twitter.util.SynchronizedLruMap
import sss.asado.MessageKeys
import sss.asado.MessageKeys.decode
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.ledger._
import sss.asado.network.MessageRouter.{Register, UnRegister}
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoStateProtocol.{NotReadyEvent, RemoteLeaderEvent, StopAcceptingTransactions}
import sss.asado.util.SeqSerializer
import sss.asado.util.ByteArrayEncodedStrOps._

/**
  * Created by alan on 4/1/16.
  */
case class Forward(who: Connection)


class TxForwarderActor(messageRouter: ActorRef,
                       clientRefCacheSize: Int)
  extends Actor with ActorLogging with AsadoEventSubscribedActor {


  private var txs = new SynchronizedLruMap[String, ActorRef](clientRefCacheSize)

  log.info("TxForwarder actor has started...")

  private def noForward: Receive = {

    case RemoteLeaderEvent(con) => self ! Forward(con)

    case Forward(who) =>
      messageRouter ! Register(MessageKeys.SignedTx)
      messageRouter ! Register(MessageKeys.SeqSignedTx)
      messageRouter ! Register(MessageKeys.SignedTxAck)
      messageRouter ! Register(MessageKeys.SignedTxNack)
      messageRouter ! Register(MessageKeys.AckConfirmTx)
      messageRouter ! Register(MessageKeys.TempNack)

      context watch who.handlerRef
      context.become(forwardMode(who.handlerRef))

  }

  private def forwardMode(leaderRef: ActorRef): Receive = {

    case NotReadyEvent =>  self ! StopAcceptingTransactions

    case Terminated(leaderRef) => self ! StopAcceptingTransactions

    case StopAcceptingTransactions =>
      messageRouter ! UnRegister(MessageKeys.SignedTx)
      messageRouter ! UnRegister(MessageKeys.SeqSignedTx)
      messageRouter ! UnRegister(MessageKeys.SignedTxAck)
      messageRouter ! UnRegister(MessageKeys.SignedTxNack)
      messageRouter ! UnRegister(MessageKeys.AckConfirmTx)
      messageRouter ! UnRegister(MessageKeys.TempNack)
      context.become(noForward)


    case m @ NetworkMessage(MessageKeys.SignedTx, bytes) =>
      decode(MessageKeys.SignedTx, bytes.toLedgerItem) { stx =>
        txs +=  (stx.txId.toBase64Str -> sender())
        leaderRef ! m
      }

    case m @ NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>
      decode(MessageKeys.SeqSignedTx, SeqSerializer.fromBytes(bytes)) { seqStx =>
        seqStx foreach { stx =>
          val le: LedgerItem = stx.toLedgerItem
          txs += (le.txId.toBase64Str -> sender())
        }
        leaderRef ! m
      }

    case m @ NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      decode(MessageKeys.SignedTxAck, bytes.toBlockChainIdTx) { txAck =>
        txs.get(txAck.blockTxId.txId.toBase64Str) map (_ ! m)
      }

    case m @ NetworkMessage(MessageKeys.TempNack, bytes) =>
      decode(MessageKeys.TempNack, bytes.toTxMessage) { tempNack =>
        txs.get(tempNack.txId.toBase64Str) map (_ ! m)
      }

    case m @ NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      decode(MessageKeys.SignedTxNack, bytes.toTxMessage) { txNack =>
        txs.get(txNack.txId.toBase64Str) map (_ ! m)
        txs -= txNack.txId.toBase64Str
      }



    case m @ NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      decode(MessageKeys.AckConfirmTx, bytes.toBlockChainIdTx) { txAck =>
        txs.get(txAck.blockTxId.txId.toBase64Str) map (_ ! m)

      }
  }

  final override def receive = noForward

}
