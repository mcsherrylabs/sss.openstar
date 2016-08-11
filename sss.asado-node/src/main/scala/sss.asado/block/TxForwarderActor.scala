package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.agent.Agent
import block._
import com.twitter.util.SynchronizedLruMap
import sss.asado.MessageKeys
import sss.asado.MessageKeys.decode
import sss.asado.ledger._
import sss.asado.network.MessageRouter.{Register, UnRegister}
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoStateProtocol.{NotReadyEvent, RegisterStateEvents, RemoteLeaderEvent, StopAcceptingTransactions}
import sss.asado.util.SeqSerializer


/**
  * Created by alan on 4/1/16.
  */
case class Forward(who: Connection)


class TxForwarderActor(
                       stateMachine: ActorRef,
                       messageRouter: ActorRef,
                       clientRefCacheSize: Int
                             ) extends Actor with ActorLogging {

  private var txs = new SynchronizedLruMap[String, ActorRef](clientRefCacheSize)

  stateMachine ! RegisterStateEvents

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
        txs +=  (stx.txId.asHexStr -> sender())
        leaderRef ! m
      }

    case m @ NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>
      decode(MessageKeys.SeqSignedTx, SeqSerializer.fromBytes(bytes)) { seqStx =>
        seqStx foreach { stx =>
          val le: LedgerItem = stx.toLedgerItem
          txs += (le.txId.asHexStr -> sender())
        }
        leaderRef ! m
      }

    case m @ NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      decode(MessageKeys.SignedTxAck, bytes.toBlockChainIdTx) { txAck =>
        txs.get(txAck.blockTxId.txId.asHexStr) map (_ ! m)
      }

    case m @ NetworkMessage(MessageKeys.TempNack, bytes) =>
      decode(MessageKeys.TempNack, bytes.toTxMessage) { tempNack =>
        txs.get(tempNack.txId.asHexStr) map (_ ! m)
      }

    case m @ NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      decode(MessageKeys.SignedTxNack, bytes.toTxMessage) { txNack =>
        txs.get(txNack.txId.asHexStr) map (_ ! m)
        txs -= txNack.txId.asHexStr
      }



    case m @ NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      decode(MessageKeys.AckConfirmTx, bytes.toBlockChainIdTx) { txAck =>
        txs.get(txAck.blockTxId.txId.asHexStr) map (_ ! m)

      }
  }

  final override def receive = noForward

}
