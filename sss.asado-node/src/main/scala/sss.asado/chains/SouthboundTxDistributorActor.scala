package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props, SupervisorStrategy}
import org.joda.time.DateTime
import sss.asado.account.{NodeIdentity, PublicKeyAccount}
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.{BlockChain, BlockChainLedger, BlockChainSignatures, BlockChainSignaturesAccessor, DistributeClose, NotSynchronized}
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.LeaderElectionActor.{LeaderLost, LocalLeader}
import sss.asado.chains.SouthboundTxDistributorActor._
import sss.asado.common.block._
import sss.asado.ledger.{LedgerItem, Ledgers}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.{AsadoEvent, MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db

import scala.language.postfixOps
import scala.util.{Failure, Success}

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

object SouthboundTxDistributorActor {

  type QuorumCandidates = () => Set[UniqueNodeIdentifier]

  private case class TryClose(nodeId: UniqueNodeIdentifier, dc: DistributeClose, retryCount: Int = 10)
  case class SouthboundRejectedTx(blockChainTxId: BlockChainTxId) extends AsadoEvent
  case class SouthboundTx(blockChainTx: BlockChainTx) extends AsadoEvent
  case class SouthboundClose(close: DistributeClose) extends AsadoEvent

  case class SynchronizedConnection(chainId: GlobalChainIdMask, nodeId: UniqueNodeIdentifier) extends AsadoEvent

  case class CheckedProps(value:Props, name:String)

  def props(
            nodeIdentity: NodeIdentity,
            q: QuorumCandidates,
            bc: BlockChainSignaturesAccessor,
            disconnect: UniqueNodeIdentifier => Unit
           )
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageEventBus: MessageEventBus,
            ledgers:Ledgers
            ): CheckedProps =

    CheckedProps(Props(classOf[SouthboundTxDistributorActor], nodeIdentity , q, bc, disconnect, db, chainId, send, messageEventBus, ledgers), s"SouthboundTxDistributorActor_$chainId")


  def apply(p:CheckedProps)(implicit actorSystem: ActorSystem): ActorRef = {
    actorSystem.actorOf(p.value.withDispatcher("blocking-dispatcher"), p.name)
  }
}

private class SouthboundTxDistributorActor(
                                            nodeIdentity: NodeIdentity,
                                            quorumCandidates: QuorumCandidates,
                                            bc: BlockChain with BlockChainSignaturesAccessor,
                                            disconnect: UniqueNodeIdentifier => Unit
                    )(implicit db: Db,
                      chainId: GlobalChainIdMask,
                      send: Send,
                      messageEventBus: MessageEventBus,
                      ledgers:Ledgers)
    extends Actor
    with ActorLogging {


  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  messageEventBus.subscribe(classOf[SouthboundTx])
  messageEventBus.subscribe(classOf[SouthboundClose])
  messageEventBus.subscribe(classOf[SynchronizedConnection])
  messageEventBus.subscribe(classOf[ConnectionLost])
  messageEventBus.subscribe(classOf[NotSynchronized])
  messageEventBus.subscribe(classOf[LocalLeader])
  messageEventBus.subscribe(MessageKeys.CommittedTx)
  messageEventBus.subscribe(MessageKeys.NonQuorumBlockNewSig)
  messageEventBus.subscribe(MessageKeys.NonQuorumCloseBlock)
  messageEventBus.subscribe(MessageKeys.QuorumRejectedTx)


  log.info("SouthboundTxDistributor actor has started...")

  override def postStop(): Unit = { log.debug("SouthboundTxDistributor actor stopped ")}

  private var weAreLeader = false

  private var syncedNodes: Set[UniqueNodeIdentifier] = Set()

  override def receive: Receive = {

    case c @ IncomingMessage(
                            `chainId`,
                            MessageKeys.NonQuorumCloseBlock,
                            nodeId,
                            dc: DistributeClose
                            ) =>

      self ! TryClose(nodeId, dc)

    case TryClose(nodeId, dc@DistributeClose(blockSigs, blockId), 0) =>
      log.error("Cannot close block {} and stopped trying ", blockId)

    case t@ TryClose(nodeId, dc@DistributeClose(blockSigs, blockId), retryCount) =>
      bc.blockHeaderOpt(blockId.blockHeight) match {
        case None =>
          bc.closeBlock(blockId) match {

            case Failure(e) =>
              log.error("Couldn't close block {} {} will retry ...", blockId, e)
              import concurrent.duration._
              import context.dispatcher
              context.system.scheduler.scheduleOnce(50 millis, self, t.copy(retryCount = retryCount - 1))

            case Success(header) =>
              log.info("Close block h:{} numTxs: {}", header.height, header.numTxs)
              val sigsOk = blockSigs.forall {
                sig =>
                  val isOk = PublicKeyAccount(sig.publicKey).verify(sig.signature, header.hash)
                  log.info("Post close block, verify sig from {} is {}", sig.nodeId, isOk)
                  isOk
              }

              require(sigsOk, "Cannot continue block sig from quorum doesn't match that of local header hash")

              val blockSignatures = BlockSignatures.QuorumSigs(blockId.blockHeight)
              blockSignatures.write(blockSigs)

              if (blockSignatures
                .indexOfBlockSignature(nodeIdentity.id)
                .isEmpty) {
                val blockHeader = bc.blockHeader(blockId.blockHeight)
                val sig = nodeIdentity.sign(blockHeader.hash)

                val newSig = BlockSignature(0,
                  new DateTime(),
                  blockHeader.height,
                  nodeIdentity.id,
                  nodeIdentity.publicKey,
                  sig)

                send(MessageKeys.NonQuorumBlockNewSig, newSig, nodeId)
              }
              self ! SouthboundClose(dc)
          }
        case Some(header) =>
          log.error("BlockHeader {} already exists, we are redistributing?",
            header)
      }


    case IncomingMessage(`chainId`, MessageKeys.QuorumRejectedTx, leader, blkChnTxId : BlockChainTxId) =>

      BlockChainLedger(blkChnTxId.height).rejected(blkChnTxId) match {
        case Failure(e) =>
          log.info("Blockchain sync failed to reject  tx {}", e)

        case Success(_) =>
      }
      self ! SouthboundRejectedTx(blkChnTxId)

    case IncomingMessage(`chainId`, MessageKeys.CommittedTx, leader, blkChnTx @ BlockChainTx(height, blockTx)) =>

      BlockChainLedger(height).apply(blockTx) match {
        case Failure(e) =>
          log.info("Blockchain sync failed {}", e)

        case Success(events) => events foreach (messageEventBus.publish)
      }
      self ! SouthboundTx(blkChnTx)

    case NotSynchronized(`chainId`) =>
      syncedNodes foreach (disconnect)

    case ConnectionLost(someNode) =>
      syncedNodes -= someNode

    case SynchronizedConnection(`chainId`, someNode) =>
      //we owe these the new txs and closes *if* we are not leader

      if(weAreLeader && !quorumCandidates().contains(someNode))
        disconnect(someNode)
      else
        syncedNodes += someNode


    case _ : LocalLeader => weAreLeader = true
    case _ : LeaderLost  => weAreLeader = false


    case IncomingMessage(`chainId`,
                          MessageKeys.NonQuorumBlockNewSig,
                          nodeId,
                          bs@ BlockSignature(index,
                          savedAt,
                          height,
                          sigNodeId,
                          publicKey,
                          signature))  =>
      // do something.
      log.info("Got new sig from Southside client {}", bs)
      val sig =
        bc.nonQuorumSigs(height).addSignature(signature, publicKey, nodeId)


    case SouthboundRejectedTx(blkChnTxId : BlockChainTxId) =>
      val minusQuorumCandidates = syncedNodes.diff(quorumCandidates())

      if(minusQuorumCandidates.nonEmpty) {
        send(MessageKeys.QuorumRejectedTx, blkChnTxId, minusQuorumCandidates)
      }

    case SouthboundTx(blkChnTx : BlockChainTx) =>
      val minusQuorumCandidates = syncedNodes.diff(quorumCandidates())

      if(minusQuorumCandidates.nonEmpty) {
        send(MessageKeys.CommittedTx, blkChnTx, minusQuorumCandidates)
      }


    case SouthboundClose(dc) =>
      val minusQuorumCandidates = syncedNodes.diff(quorumCandidates())

      if(minusQuorumCandidates.nonEmpty)
        send(MessageKeys.NonQuorumCloseBlock, dc, minusQuorumCandidates)

  }
}
