package sss.ui.nobu

import akka.actor.Actor

import sss.asado.MessageKeys
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.block._

import sss.asado.message.{Message, MessageInBox}
import sss.asado.network.NetworkMessage
import sss.asado.nodebuilder.ClientNode
import sss.asado.state.AsadoStateProtocol.{
  NotOrderedEvent,
  RemoteLeaderEvent,
  StateMachineInitialised
}
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.wallet.WalletPersistence.Lodgement

import sss.ui.nobu.NobuNodeBridge._
import sss.ui.reactor.UIReactor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}

/**
  * Created by alan on 11/9/16.
  */
class ClientEventActor(clientNode: ClientNode)
    extends Actor
    with AsadoEventSubscribedActor {

  private case object ConnectHome
  private case object BroadcastConnected
  private case class ConnectHomeDelay(delaySeconds: Int = 500)
  private case class Analyse(block: Long)

  import clientNode._

  var watchingBounties: Map[String, BountyTracker] = Map()
  var watchingMsgSpends: Map[String, Bag] = Map()

  messageEventBus.subscribe(MessageKeys.SignedTxAck)
  messageEventBus.subscribe(MessageKeys.AckConfirmTx)
  messageEventBus.subscribe(MessageKeys.TempNack)
  messageEventBus.subscribe(MessageKeys.SignedTxNack)

  override def receive: Receive = connecting orElse business

  private def connecting: Receive = {
    case RemoteLeaderEvent(conn) =>
      context.become(connected(conn.nodeId) orElse business)
      self ! BroadcastConnected

    case ConnectHomeDelay(delay) =>
      context.system.scheduler
        .scheduleOnce(FiniteDuration(delay, SECONDS), self, ConnectHome)

    case ConnectHome =>
      connectHome
      self ! ConnectHomeDelay()

  }

  private def connected(connectedTo: String): Receive = {
    case NotOrderedEvent =>
      UIReactor.eventBroadcastActorRef ! LostConnection
      context.become(connecting orElse business)
      self ! ConnectHomeDelay()

    case BroadcastConnected =>
      UIReactor.eventBroadcastActorRef ! Connected(connectedTo)
      context.system.scheduler
        .scheduleOnce(FiniteDuration(30, SECONDS), self, BroadcastConnected)

    case ConnectHome => log.info("Already connected, ignore ConnectHome")
  }

  private def business: Receive = {
    case StateMachineInitialised =>
      ncRef
      self ! ConnectHomeDelay(3)

    case b @ Bag(userWallet,
                 signedTx,
                 savedAddressedMessage,
                 walletUpdate,
                 from) =>
      val sndr = sender()
      //val le = LedgerItem(MessageKeys.BalanceLedger, signedTx.txId, signedTx.toBytes)
      val netMsg = NetworkMessage(
        MessageKeys.SignedTx,
        savedAddressedMessage.addrMsg.ledgerItem.toBytes)
      watchingMsgSpends += savedAddressedMessage.addrMsg.ledgerItem.txIdHexStr -> b
      clientNode.ncRef.send(netMsg, clientNode.homeDomain.nodeId.id)

    case b @ BountyTracker(sender, userWallet, txIndex, out, le) =>
      watchingBounties += txIndex.txId.toBase64Str -> b
      clientNode.ncRef.send(
        NetworkMessage(MessageKeys.SignedTx, le.toBytes),
        clientNode.homeDomain.nodeId.id)

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      val txId = bId.blockTxId.txId
      watchingBounties.get(bId.blockTxId.txId.toBase64Str) map {
        bountyTracker =>
          bountyTracker.wallet.credit(
            Lodgement(bountyTracker.txIndex,
                      bountyTracker.txOutput,
                      bId.height))
          watchingBounties -= bId.blockTxId.txId.toBase64Str
          bountyTracker.sndr ! Show(
            s"ca-ching! ${bountyTracker.txOutput.amount}")
      }
      watchingMsgSpends.get(txId.toBase64Str).map { bag =>
        watchingMsgSpends -= txId.toBase64Str
        val walletUpdate = bag.walletUpdate
        val msg = Message(bag.from,
                          bag.msg.addrMsg.msgPayload,
                          bag.sTx.toBytes,
                          0,
                          bag.msg.savedAt)

        MessageInBox(bag.msg.to).addNew(msg)
        bag.userWallet
          .update(walletUpdate.txId, walletUpdate.debits, walletUpdate.credits)
        bag.walletUpdate.sndr ! Show(s"Message accepted!")
      }

    case NetworkMessage(MessageKeys.NackConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      //push(Notification.show(s"Got NAckConfirm $bId"))
      watchingBounties -= bId.blockTxId.txId.toBase64Str

    case NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      val bId = bytes.toBlockChainIdTx
    //push(Notification.show(s"Got ACK $bId"))

    case NetworkMessage(MessageKeys.TempNack, bytes) =>
      val m = bytes.toTxMessage
      //push(Notification.show(s"Got NACK ${m.msg}"))
      watchingBounties -= m.txId.toBase64Str
      watchingMsgSpends -= m.txId.toBase64Str

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      val m = bytes.toTxMessage
      //push(Notification.show(s"Got NACK ${m.msg}"))
      watchingBounties -= m.txId.toBase64Str
      watchingMsgSpends -= m.txId.toBase64Str

  }
}
