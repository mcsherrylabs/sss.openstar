package sss.asado.block


import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import block._
import sss.asado.MessageKeys
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol.Synced
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 3/24/16.
  */
case class ClientSynched(ref: ActorRef, currentBlockHeight: Long, expectedNextMessage: Long)


class BlockChainSynchronizationActor(quorum: Int,
                                     stateMachine: ActorRef,
                                     bc: BlockChain,
                                     messageRouter: ActorRef)(implicit db: Db) extends Actor with ActorLogging with ByteArrayComparisonOps {

  messageRouter ! Register(MessageKeys.NackConfirmTx)
  messageRouter ! Register(MessageKeys.AckConfirmTx)
  messageRouter ! Register(MessageKeys.GetPageTx)

  private case class ClientTx(client : ActorRef, blockChainTxId: BlockChainTxId)

  private def awaitConfirms(updateToDatePeers: Set[ActorRef], awaitGroup: Map[ActorRef, List[ClientTx]]): Receive = {
    case DistributeTx(client, btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>

      def toMapElement(upToDatePeer: ActorRef) = {
        upToDatePeer ! NetworkMessage(MessageKeys.ConfirmTx,btx.toBytes)
        upToDatePeer -> (awaitGroup(upToDatePeer) :+ ClientTx(client, btx.toId))
      }

      context.become(awaitConfirms(updateToDatePeers, updateToDatePeers.map(toMapElement).toMap.withDefaultValue(Nil)))


    case ReDistributeTx(btx) =>
      updateToDatePeers.foreach(_ ! NetworkMessage(MessageKeys.ConfirmTx,btx.toBytes))

    case DistributeClose(bId @ BlockId(blockheight, numTxs)) =>
      updateToDatePeers foreach (_ ! NetworkMessage(MessageKeys.CloseBlock, bId.toBytes))

    case netTxPage @ NetworkMessage(MessageKeys.GetPageTx, bytes) =>
      val ref = context.actorOf(Props(classOf[TxPageActor], bc, db))
      ref forward netTxPage

    case NetworkMessage(MessageKeys.NackConfirmTx, blockChainTxIdNackedBytes) =>
      val sndr = sender()
      Try {
        val blockChainTxIdNacked = blockChainTxIdNackedBytes.toBlockChainIdTx
        val newMap = awaitGroup(sndr).filter { ctx =>
          if (ctx.blockChainTxId == blockChainTxIdNacked) {
            //Yes. side effects.
            ctx.client ! NetworkMessage(MessageKeys.NackConfirmTx, blockChainTxIdNackedBytes)
            false
          } else true
        } match {
          case Nil => awaitGroup - sndr
          case remainingList => awaitGroup + (sndr -> remainingList)
        }
        context.become(awaitConfirms(updateToDatePeers, newMap.withDefaultValue(Nil)))
      } match {
        case Failure(e) => log.error(e, "Didn't handle Nack to client correctly.")
        case Success(_) =>
      }

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val sndr = sender()
      Try {
        val confirm = bytes.toBlockChainIdTx
        bc.confirm(confirm)

        val newMap = awaitGroup(sndr).filter { ctx =>
          if (ctx.blockChainTxId == confirm) {
            //Yes. side effects.
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

    case ClientSynched(clientRef, currentBlock, nextMessageIndex) =>
      context watch clientRef
      val newPeerSet = updateToDatePeers + clientRef
      context.become(awaitConfirms(newPeerSet, awaitGroup))
      if(newPeerSet.size == quorum) stateMachine ! Synced
      clientRef ! NetworkMessage(MessageKeys.Synced, Array())
  }

  override def receive: Receive = awaitConfirms(Set.empty, Map.empty[ActorRef, List[ClientTx]].withDefaultValue(Nil))
}
