package sss.asado.block


import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import block._
import sss.asado.MessageKeys
import sss.asado.Node.InitWithActorRefs
import sss.asado.network.MessageRouter.{Register, RegisterRef}
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol.{QuorumLost, Synced}
import sss.asado.util.ByteArrayComparisonOps
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 3/24/16.
  */
case class ClientSynched(ref: ActorRef, lastTxPage: GetTxPage)
case class EnsureConfirms[T](ref: ActorRef, height: Long, t: T, retryCount: Int = 1)

class BlockChainSynchronizationActor(quorum: Int,
                                     stateMachine: ActorRef,
                                     bc: BlockChain with BlockChainTxConfirms,
                                     messageRouter: ActorRef)(implicit db: Db) extends Actor with ActorLogging with ByteArrayComparisonOps {

  val pageResponder = context.actorOf(Props(classOf[TxPageActor], bc))

  messageRouter ! Register(MessageKeys.NackConfirmTx)
  messageRouter ! Register(MessageKeys.AckConfirmTx)
  messageRouter ! RegisterRef(MessageKeys.GetPageTx, pageResponder)

  private def init: Receive = {
    case InitWithActorRefs(blockChainActor) =>
      context.become(awaitConfirms(blockChainActor,Set.empty, Map.empty[ActorRef, List[ClientTx]].withDefaultValue(Nil)))
  }

  private case class ClientTx(client : ActorRef, blockChainTxId: BlockChainTxId)

  private def awaitConfirms(blockChainActor: ActorRef, updateToDatePeers: Set[ActorRef], awaitGroup: Map[ActorRef, List[ClientTx]]): Receive = {
    case DistributeTx(client, btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>

      def toMapElement(upToDatePeer: ActorRef) = {
        upToDatePeer ! NetworkMessage(MessageKeys.ConfirmTx,btx.toBytes)
        upToDatePeer -> (awaitGroup(upToDatePeer) :+ ClientTx(client, btx.toId))
      }

      context.become(awaitConfirms(blockChainActor, updateToDatePeers, updateToDatePeers.map(toMapElement).toMap.withDefaultValue(Nil)))

    case ensuresConfirms @ EnsureConfirms(replyTo, height, msg, retryCount) =>
      bc.getUnconfirmed(height, updateToDatePeers.size) match {
        case unconfirmedEmpty if unconfirmedEmpty.isEmpty => replyTo ! msg
        case unconfirmed =>
          log.warning(s"There were ${unconfirmed.size} unconfirmed txs, retry ${retryCount}...")
          if(retryCount % 2 == 0)  unconfirmed.foreach (unconfirmedTx => self ! ReDistributeTx(unconfirmedTx))

          context.system.scheduler.scheduleOnce(
            FiniteDuration(retryCount * 2, SECONDS ),
            self, ensuresConfirms.copy(retryCount = retryCount + 1))


      }

    case ReDistributeTx(btx) =>
      updateToDatePeers.foreach(_ ! NetworkMessage(MessageKeys.ConfirmTx,btx.toBytes))

    case DistributeClose(sig, bId @ BlockId(blockheight, numTxs)) =>
      updateToDatePeers foreach (_ ! NetworkMessage(MessageKeys.CloseBlock, bId.toBytes))

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
        context.become(awaitConfirms(blockChainActor, updateToDatePeers, newMap.withDefaultValue(Nil)))
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

        context.become(awaitConfirms(blockChainActor, updateToDatePeers, newMap.withDefaultValue(Nil)))
      } match {
        case Failure(e) => log.error(e, "Didn't handle confirm correctly.")
        case Success(_) =>
      }

    case Terminated(deadClient) =>
      val newAwaitGroup = awaitGroup.filterNot(kv => kv._1 == deadClient).withDefaultValue(Nil)
      val newPeerSet = updateToDatePeers - deadClient
      if (newPeerSet.size < quorum) stateMachine ! QuorumLost
      context.become(awaitConfirms(blockChainActor, newPeerSet, newAwaitGroup))

    case CommandFailed(lastTxPage) =>
      log.error(s"Blockchain FAILED to start up again,try again later")
      context.system.scheduler.scheduleOnce(5 seconds, blockChainActor, StartBlockChain(self, lastTxPage))

    case BlockChainStarted(lastTxPage) =>
      log.info(s"Blockchain started up again, client synced to $lastTxPage")

    case ClientSynched(clientRef, lastTxPage) =>
      context watch clientRef
      val newPeerSet = updateToDatePeers + clientRef
      context.become(awaitConfirms(blockChainActor, newPeerSet, awaitGroup))
      if (newPeerSet.size == quorum) stateMachine ! Synced
      clientRef ! NetworkMessage(MessageKeys.Synced, lastTxPage.toBytes)
      blockChainActor ! StartBlockChain(self, lastTxPage)


    case sbc @ StopBlockChain(_, _) => blockChainActor ! sbc
  }

  override def receive: Receive = init
}
