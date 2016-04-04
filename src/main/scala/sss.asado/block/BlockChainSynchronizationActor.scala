package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import block._
import sss.asado.MessageKeys
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol.Synced
import sss.asado.storage.TxDBStorage
import sss.db.Db

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 3/24/16.
  */
case class ClientSynched(ref: ActorRef)
case class DistributeClose(blockNumber: Long)

class BlockChainSynchronizationActor(quorum: Int,
                                     stateMachine: ActorRef,
                                     bc: BlockChain,
                                     messageRouter: ActorRef)(implicit db: Db) extends Actor with ActorLogging {

  messageRouter ! Register(MessageKeys.AckConfirmTx)
  messageRouter ! Register(MessageKeys.GetTxPage)

  private case class ClientTx(client : ActorRef, height: Long, id: Long)

  private def awaitConfirms(updateToDatePeers: Set[ActorRef], awaitGroup: Map[ActorRef, List[ClientTx]]): Receive = {
    case DistributeTx(client, signedTx, height, id) =>

      def toMapElement(upToDatePeer: ActorRef) = {
        upToDatePeer ! NetworkMessage(MessageKeys.ConfirmTx, signedTx.toBytes)
        upToDatePeer -> (awaitGroup(upToDatePeer) :+ ClientTx(client, height, id))
      }

      context.become(awaitConfirms(updateToDatePeers, updateToDatePeers.map(toMapElement).toMap.withDefaultValue(Nil)))


    case DistributeClose(blockNumber) =>
      updateToDatePeers foreach (_ ! NetworkMessage(MessageKeys.CloseBlock, Array()))

    case netTxPage @ NetworkMessage(MessageKeys.GetTxPage, bytes) =>
      val ref = context.actorOf(Props(classOf[TxPageActor], bc, db))
      ref forward netTxPage

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val sndr = sender()
      Try {
        val confirm = bytes.toAckConfirmTx
        addConfirmation(confirm)

        val newMap = awaitGroup(sndr).filter { ctx =>
          if (ctx.id == confirm.id && ctx.height == confirm.height) {
            ctx.client ! NetworkMessage(MessageKeys.AckConfirmTx, bytes)
            false
          } else true
        } match {
          case Nil => awaitGroup - sndr
          case remainingList => awaitGroup + (sndr -> remainingList)
        }

        context.become(awaitConfirms(updateToDatePeers, newMap.withDefaultValue(Nil)))
      } match {
        case Failure(e) => log.error(e, "Didn't handle confirm correctly.")
        case Success(_) =>
      }

    case Terminated(deadClient) =>
      val newAwaitGroup = awaitGroup.filterNot(kv => kv._1 == deadClient).withDefaultValue(Nil)
      val newPeerSet = updateToDatePeers - deadClient
      context.become(awaitConfirms(newPeerSet, newAwaitGroup))

    case ClientSynched(clientRef) =>
      context watch clientRef
      val newPeerSet = updateToDatePeers + clientRef
      context.become(awaitConfirms(newPeerSet, awaitGroup))
      if(newPeerSet.size == quorum) stateMachine ! Synced
      clientRef ! NetworkMessage(MessageKeys.Synced, Array())
  }

  private def addConfirmation(confirm: AckConfirmTx) = TxDBStorage.confirm(confirm.txId, confirm.height, confirm.id)

  override def receive: Receive = awaitConfirms(Set.empty, Map.empty[ActorRef, List[ClientTx]].withDefaultValue(Nil))
}
