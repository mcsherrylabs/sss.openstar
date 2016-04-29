package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.agent.Agent
import block._
import com.twitter.util.SynchronizedLruMap
import ledger._
import sss.asado.MessageKeys
import sss.asado.MessageKeys.decode
import sss.asado.network.MessageRouter.{Register, UnRegister}
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoStateProtocol.StopAcceptingTransactions
import sss.asado.util.ByteArrayVarcharOps._

/**
  * Created by alan on 4/1/16.
  */
case class Forward(leader: String)


class TxForwarderActor(thisNodeId: String,
                       connectedPeers: Agent[Set[Connection]],
                       messageRouter: ActorRef,
                       clientRefCacheSize: Int
                             ) extends Actor with ActorLogging {


  private var txs = new SynchronizedLruMap[String, ActorRef](clientRefCacheSize)

  private def noForward: Receive = {
    case Forward(leader) =>
      messageRouter ! Register(MessageKeys.SignedTx)
      messageRouter ! Register(MessageKeys.SeqSignedTx)
      messageRouter ! Register(MessageKeys.SignedTxAck)
      messageRouter ! Register(MessageKeys.SignedTxNack)
      messageRouter ! Register(MessageKeys.AckConfirmTx)

      connectedPeers().find(conn => conn.nodeId.id == leader) match {
        case None => log.error(s"Cannot forward to leader $leader, connection not found.")
        case Some(conn) =>
          context watch conn.handlerRef
          context.become(forwardMode(conn.handlerRef))
      }

  }
  private def forwardMode(leaderRef: ActorRef): Receive = {

    case Terminated(leaderRef) => self ! StopAcceptingTransactions

    case StopAcceptingTransactions =>
      messageRouter ! UnRegister(MessageKeys.SignedTx)
      messageRouter ! UnRegister(MessageKeys.SeqSignedTx)
      messageRouter ! UnRegister(MessageKeys.SignedTxAck)
      messageRouter ! UnRegister(MessageKeys.SignedTxNack)
      messageRouter ! UnRegister(MessageKeys.AckConfirmTx)
      context.become(noForward)


    case m @ NetworkMessage(MessageKeys.SignedTx, bytes) =>
      decode(MessageKeys.SignedTx, bytes.toSignedTx) { stx =>
        txs +=  (stx.txId.toVarChar -> sender())
        leaderRef ! m
      }

    case m @ NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>
      decode(MessageKeys.SeqSignedTx, bytes.toSeqSignedTx) { seqStx =>
        seqStx.ordered foreach { stx =>
          txs += (stx.txId.toVarChar -> sender())
        }
        leaderRef ! m
      }

    case m @ NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      decode(MessageKeys.SignedTxAck, bytes.toBlockChainIdTx) { txAck =>
        txs.get(txAck.blockTxId.txId.toVarChar) map (_ ! m)

      }

    case m @ NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      decode(MessageKeys.SignedTxNack, bytes.toTxMessage) { txNack =>
        txs.get(txNack.txId.toVarChar) map (_ ! m)
        txs -= txNack.txId.toVarChar
      }



    case m @ NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      decode(MessageKeys.AckConfirmTx, bytes.toBlockChainIdTx) { txAck =>
        txs.get(txAck.blockTxId.txId.toVarChar) map (_ ! m)

      }
  }

  final override def receive = noForward

}
