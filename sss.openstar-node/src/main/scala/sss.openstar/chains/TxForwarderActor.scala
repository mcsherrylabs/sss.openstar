package sss.openstar.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.twitter.util.SynchronizedLruMap
import sss.openstar.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.openstar.actor.OpenstarEventSubscribedActor
import sss.openstar.block.{IsSynced, NotSynchronized, Synchronized}
import sss.openstar.network.MessageEventBus._
import sss.openstar.common.block._
import sss.openstar.util.ByteArrayEncodedStrOps._
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.chains.LeaderElectionActor.{LeaderFound, LeaderLost, LocalLeader, RemoteLeader}
import sss.openstar.chains.TxWriterActor.{InternalLedgerItem, InternalResponse, NetResponse, Response}
import sss.openstar.ledger.{LedgerItem, SeqLedgerItem}
import sss.openstar.network.{MessageEventBus, _}


object TxForwarderActor {

  def apply(clientRefCacheSize: Int)(implicit actorSystem:ActorSystem,
                                     chainId: GlobalChainIdMask,
                                     send: Send,
                                     messageRouter: MessageEventBus): ActorRef = {

    actorSystem.actorOf(
      Props(classOf[TxForwarderActor],
        clientRefCacheSize, chainId, send, messageRouter), s"TxForwarderActor_$chainId"
    )
  }
}

class TxForwarderActor(clientRefCacheSize: Int)(implicit chainId: GlobalChainIdMask,
                                                send: Send,
                                                messageRouter: MessageEventBus)
  extends Actor with ActorLogging with OpenstarEventSubscribedActor {

  private var txs = new SynchronizedLruMap[String, Response](clientRefCacheSize)

  log.info("TxForwarder actor has started...")

  Seq(classOf[LeaderFound], classOf[LeaderLost], classOf[IsSynced]).subscribe

  val txIncomingMessages = Seq(MessageKeys.SeqSignedTx, MessageKeys.SignedTx)

  txIncomingMessages.subscribe

  val restTxProcessingMessages = Seq(
    MessageKeys.SignedTxConfirm,
    MessageKeys.SignedTxNack,
    MessageKeys.SignedTxAck,
    MessageKeys.TempNack)

  val internalTxProcessingMsgs = Seq(classOf[InternalLedgerItem])

  internalTxProcessingMsgs.subscribe

  private def forwardingUpstream(upstreamNodeId: UniqueNodeIdentifier): Receive = forwardingImpl(upstreamNodeId) orElse localLeader orElse {

    case RemoteLeader(`chainId`, newLeader, _) =>
      txIncomingMessages.subscribe
      context become forwardingLeader(newLeader)

    case NotSynchronized(`chainId`) =>
      context become waitForRemote
      restTxProcessingMessages.unsubscribe
      internalTxProcessingMsgs.unsubscribe
  }

  private def forwardingLeader(leader: UniqueNodeIdentifier): Receive = forwardingImpl(leader) orElse localLeader orElse {

    case LeaderLost(`chainId`, `leader`) =>
      context become waitForRemote
      restTxProcessingMessages.unsubscribe
      internalTxProcessingMsgs.unsubscribe
  }

  private def forwardingImpl(nodeIdToForwardTo: UniqueNodeIdentifier): Receive = {
    case InternalLedgerItem(`chainId`, le, listener) =>
      txs += (le.txIdHexStr -> InternalResponse(listener))
      send(MessageKeys.SignedTx, le, nodeIdToForwardTo)

    case m @ IncomingMessage(`chainId`, MessageKeys.SignedTx, nodeId, le@ LedgerItem(_,txId, _)) =>
      txs += (le.txIdHexStr -> NetResponse(nodeId, send))
      send(MessageKeys.SignedTx, le, nodeIdToForwardTo)

    case m @ IncomingMessage(`chainId`, MessageKeys.SeqSignedTx, nodeId, seqLe: SeqLedgerItem) =>
      seqLe.value foreach { le =>
        txs += (le.txIdHexStr -> NetResponse(nodeId, send))
        send(MessageKeys.SignedTx, le, nodeIdToForwardTo)
      }

    case m @ IncomingMessage(`chainId`, MessageKeys.SignedTxAck, nodeId, blk @ BlockChainTxId(_, txId)) =>
      txs.get(txId.txId.toBase64Str) map (_.ack(blk))


    case m @ IncomingMessage(`chainId`, MessageKeys.SignedTxNack, nodeId, txMsg: TxMessage) =>
      txs.get(txMsg.txId.toBase64Str) map { nId =>
        nId.nack(txMsg)
        txs -= txMsg.txId.toBase64Str
      }

    case m @ IncomingMessage(`chainId`, MessageKeys.TempNack, nodeId, txMsg: TxMessage) =>
      txs.get(txMsg.txId.toBase64Str) map (_.tempNack(txMsg))


    case m @ IncomingMessage(`chainId`, MessageKeys.SignedTxConfirm, nodeId, blk @ BlockChainTxId(_, txId)) =>
      txs.get(blk.blockTxId.txId.toBase64Str) map { resp =>
        resp.confirm(blk)
        txs -= blk.blockTxId.txId.toBase64Str
      }

  }

  final private def localLeader: Receive = {

    case _: LocalLeader =>
      txIncomingMessages.unsubscribe
      restTxProcessingMessages.unsubscribe
      internalTxProcessingMsgs.unsubscribe
      context become waitForRemote
  }


  final override def receive = waitForRemote


  final private def waitForRemote: Receive = localLeader orElse {

    case InternalLedgerItem(`chainId`, le, listener) =>
      InternalResponse(listener).tempNack(TxMessage(0, le.txId, "This node does not know where to send this tx"))

    case m @ IncomingMessage(`chainId`, MessageKeys.SignedTx, nodeId, le@ LedgerItem(_,txId, _)) =>
      //TODO BLACKLIST IF CLIENT PERSISTS
      send(MessageKeys.TempNack, TxMessage(0, txId, "This node does not know where to send this message"), nodeId)

    case m @ IncomingMessage(`chainId`, MessageKeys.SeqSignedTx, nodeId, seqLe: SeqLedgerItem) =>
      //TODO BLACKLIST IF CLIENT PERSISTS
      seqLe.value.headOption map (le =>
          send(MessageKeys.TempNack, TxMessage(0, le.txId, "This node does not know where to send these messages"), nodeId)
        )

    case Synchronized(`chainId`, _, _, upStreamNodeId) =>

      restTxProcessingMessages.subscribe
      internalTxProcessingMsgs.subscribe
      context become forwardingUpstream(upStreamNodeId)

    case RemoteLeader(`chainId`, leader, _) =>
      txIncomingMessages.subscribe
      restTxProcessingMessages.subscribe
      internalTxProcessingMsgs.subscribe
      context become forwardingLeader(leader)

  }

}
