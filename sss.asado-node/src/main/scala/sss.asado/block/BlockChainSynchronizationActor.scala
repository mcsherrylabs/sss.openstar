package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import sss.asado.common.block._
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.network.{MessageEventBus, NodeId, SerializedMessage}
import sss.asado.state.AsadoStateProtocol._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{InitWithActorRefs, MessageKeys}
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class ClientSynched(ref: ActorRef, lastTxPage: GetTxPage)
case class EnsureConfirms[T](ref: ActorRef,
                             height: Long,
                             t: T,
                             retryCount: Int = 1)

/**
  * This actor coordinates the distribution of tx's across the connected peers
  * - Making sure a local tx has been written on remote peers.
  * - Adding peers to the upToDate list when TxPageActor says they are synced
  * - Forward the confirms from the remote peers back to the original client.
  * - when a quorum of peers are up to date the 'Synced' event is raised with the State Machine
  *
  * @param quorum
  * @param maxTxPerBlock
  * @param maxSignatures
  * @param stateMachine
  * @param bc
  * @param messageRouter
  * @param db
  */
class BlockChainSynchronizationActor(
                                     maxTxPerBlock: Int,
                                     maxSignatures: Int,
                                     peersList: Set[NodeId],
                                     stateMachine: ActorRef,
                                     bc: BlockChain with BlockChainTxConfirms,
                                     messageRouter: MessageEventBus)(implicit db: Db, chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with AsadoEventSubscribedActor {

  val pageResponder =
    context.actorOf(Props(classOf[TxPageActor], maxSignatures, bc, db))

  messageRouter.subscribe(MessageKeys.GetPageTx)(pageResponder)
  messageRouter.subscribe(MessageKeys.BlockNewSig)(pageResponder)

  log.info("BlockChainSynchronization actor has started...")

  private def init: Receive = {
    case InitWithActorRefs(blockChainActor) =>
      context.become(awaitConfirms(blockChainActor))
  }

  //private var updateToDateClients: Set[ActorRef] = Set.empty
  private var updateToDatePeers: Set[ActorRef] = Set.empty
  private var awaitGroup: Map[ActorRef, List[ClientTx]] =
    Map.empty.withDefaultValue(Nil)

  private case class ClientTx(client: ActorRef, blockChainTxId: BlockChainTxId)

  private def awaitConfirms(blockChainActor: ActorRef): Receive = {

    case RemoteLeaderEvent(_) =>
      messageRouter.unsubscribe(MessageKeys.NackConfirmTx)
      messageRouter.unsubscribe(MessageKeys.AckConfirmTx)

    case LocalLeaderEvent =>
      messageRouter.subscribe(MessageKeys.NackConfirmTx)
      messageRouter.subscribe(MessageKeys.AckConfirmTx)

    case DistributeTx(client,
                      btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>
      def toMapElement(upToDatePeer: ActorRef) = {
        upToDatePeer ! SerializedMessage(MessageKeys.ConfirmTx, btx.toBytes)
        upToDatePeer -> (awaitGroup(upToDatePeer) :+ ClientTx(client, btx.toId))
      }
      awaitGroup =
        updateToDatePeers.map(toMapElement).toMap.withDefaultValue(Nil)
      if (index + 1 == maxTxPerBlock)
        blockChainActor ! MaxBlockSizeOrOpenTimeReached

    case ensuresConfirms @ EnsureConfirms(replyTo, height, msg, retryCount) =>
      bc.getUnconfirmed(height, updateToDatePeers.size) match {
        case unconfirmedEmpty if unconfirmedEmpty.isEmpty => replyTo ! msg
        case unconfirmed =>
          log.warning(
            s"There were ${unconfirmed.size} unconfirmed txs, retry ${retryCount}...")
          if (retryCount % 2 == 0)
            unconfirmed.foreach(unconfirmedTx =>
              self ! ReDistributeTx(unconfirmedTx))

          context.system.scheduler.scheduleOnce(
            FiniteDuration(retryCount * 2, SECONDS),
            self,
            ensuresConfirms.copy(retryCount = retryCount + 1))

      }

    case ReDistributeTx(btx) =>
      updateToDatePeers.foreach(
        _ ! SerializedMessage(MessageKeys.ConfirmTx, btx.toBytes))

    case distClose @ DistributeClose(allSigs, BlockId(blockheight, numTxs)) =>
      updateToDatePeers foreach (_ ! SerializedMessage(MessageKeys.CloseBlock,
                                                    distClose.toBytes))

    case distSig @ DistributeSig(blockSignature: BlockSignature) =>
      updateToDatePeers foreach (_ ! SerializedMessage(MessageKeys.BlockSig,
                                                    blockSignature.toBytes))

    case SerializedMessage(_, MessageKeys.NackConfirmTx, blockChainTxIdNackedBytes) =>
      val sndr = sender()
      Try {
        val blockChainTxIdNacked = blockChainTxIdNackedBytes.toBlockChainTxId
        val newMap = awaitGroup(sndr).filter { ctx =>
          if (ctx.blockChainTxId == blockChainTxIdNacked) {
            //Yes. side effects.
            ctx.client ! SerializedMessage(MessageKeys.NackConfirmTx,
                                        blockChainTxIdNackedBytes)
            false
          } else true
        } match {
          case Nil           => awaitGroup - sndr
          case remainingList => awaitGroup + (sndr -> remainingList)
        }
        awaitGroup = newMap.withDefaultValue(Nil)

      } match {
        case Failure(e) =>
          log.error(e, "Didn't handle Nack to client correctly.")
        case Success(_) =>
      }

    case SerializedMessage(_, MessageKeys.AckConfirmTx, bytes) =>
      val sndr = sender()
      Try {
        val confirm = bytes.toBlockChainTxId
        bc.confirm(confirm)

        val newMap = awaitGroup(sndr).filter { ctx =>
          if (ctx.blockChainTxId == confirm) {
            // Forward the confirm from a remote peer to the original client
            // who raised the tx.
            ctx.client ! SerializedMessage(MessageKeys.AckConfirmTx, bytes)
            false
          } else true
        } match {
          case Nil           => awaitGroup - sndr
          case remainingList => awaitGroup + (sndr -> remainingList)
        }
        awaitGroup = newMap.withDefaultValue(Nil)

      } match {
        case Failure(e) => log.error(e, "Didn't handle confirm correctly.")
        case Success(_) =>
      }

    case Terminated(deadClient) =>
      val newAwaitGroup =
        awaitGroup.filterNot(kv => kv._1 == deadClient).withDefaultValue(Nil)
      updateToDatePeers = updateToDatePeers - deadClient
      //TODO QUORUM TAKEN OUT
      //if (updateToDatePeers.size < quorum) stateMachine ! NotSynced

    case CommandFailed(lastTxPage) =>
      log.error(s"Blockchain FAILED to start up again,try again later")
      context.system.scheduler.scheduleOnce(5 seconds,
                                            blockChainActor,
                                            StartBlockChain(self, lastTxPage))

    case BlockChainStarted(lastTxPage) =>
      log.info(s"Blockchain started up again, client synced to $lastTxPage")

    case ClientSynched(clientRef, lastTxPage) =>
      context watch clientRef
      updateToDatePeers = updateToDatePeers + clientRef
      //We may be restarting the blockchain after a pause for client syncing.
      blockChainActor ! StartBlockChain(self, lastTxPage)
      //TODO REPLACE QUORUM WITH SOMETHING !!!! if (updateToDatePeers.size == quorum) stateMachine ! IsSynced
      clientRef ! SerializedMessage(MessageKeys.Synced, lastTxPage.toBytes)

    case sbc @ StopBlockChain(_, _) => blockChainActor ! sbc
  }

  override def receive: Receive = init
}
