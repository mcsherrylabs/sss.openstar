package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props, SupervisorStrategy}
import sss.asado.account.NodeIdentity
import sss.asado.actor.{AsadoEventPublishingActor, AsadoEventSubscribedActor, SystemPanic}
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.block.{BlockChain, BlockChainLedger, BlockChainSignatures, BlockChainSignaturesAccessor, BlockClosedEvent, BlockHeader, DistributeClose}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumMonitor.Quorum
import sss.asado.common.block._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{MessageKeys, Send}
import sss.db.Db

import scala.language.postfixOps
import scala.language.implicitConversions
import scala.concurrent.duration._

import scala.util.{Failure, Success}

object BlockCloseDistributorActor {

  case class CheckedProps(value: Props, name: String)

  def props(height: Long,
            ledger: BlockChainLedger,
            q: Quorum,

            bc: BlockChain with BlockChainSignaturesAccessor,
            blockChainSettings: BlockChainSettings,
            nodeIdentity: NodeIdentity)(
      implicit db: Db,
      chainId: GlobalChainIdMask,
      send: Send,
      messageEventBus: MessageEventBus): CheckedProps =
    CheckedProps(
      Props(classOf[BlockCloseDistributorActor],
                   height,
                   ledger,
                   q,

                   bc,
                   blockChainSettings,
                   nodeIdentity,
                   db,
                   chainId,
                   send,
                   messageEventBus),
      s"BlockCloseDistributorActor_${chainId}_${height}"
    )

  def apply(p: CheckedProps)(implicit context: ActorContext): ActorRef = {
    context.actorOf(p.value.withDispatcher("blocking-dispatcher"), p.name)
  }
}

private class BlockCloseDistributorActor(
                                          height: Long,
                                          ledger: BlockChainLedger,
                                          q: Quorum,

                                          bc: BlockChain with BlockChainSignaturesAccessor,
                                          blockChainSettings: BlockChainSettings,
                                          nodeIdentity: NodeIdentity)(implicit db: Db,
                                chainId: GlobalChainIdMask,
                                send: Send,
                                messageEventBus: MessageEventBus)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with SystemPanic {

  messageEventBus.subscribe(classOf[Quorum])
  messageEventBus.subscribe(MessageKeys.BlockNewSig)

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def postStop(): Unit = {
    log.debug("BlockCloseDistributor actor for {} has stopped", height)
  }

  private var currentQuorum = q

  self ! height

  private def waitForClose: Receive = {

    case IncomingMessage(`chainId`,
                         MessageKeys.BlockNewSig,
                         nodeId,
                         bs@ BlockSignature(_,
                                        savedAt,
                                        `height`,
                                        sigNodeId,
                                        publicKey,
                                        signature)) =>

      log.info("Add new sig, height {}, from {}", height,  sigNodeId)

      bc.quorumSigs(height).addSignature(signature, publicKey, nodeId) match {
        case Failure(e) =>
            log.error("bc.addSignature failure {}", e)
        case Success(sig) =>
          send(MessageKeys.BlockSig, sig, currentQuorum.members)
          val currentNumSigsForBlock = bc.quorumSigs(height).signatures(Int.MaxValue).size
          stopIfConfirmed(currentNumSigsForBlock)
      }


    case `height` =>
      bc.blockHeaderOpt(height) match {
        case None =>
          bc.blockHeaderOpt(height - 1) match {
            case Some(lastBlock) =>

              bc.closeBlock(lastBlock) match {

                case Success(newLastBlock) =>
                  messageEventBus.publish(BlockClosedEvent(newLastBlock.height))

                  val sig = bc.quorumSigs(newLastBlock.height).sign(nodeIdentity, newLastBlock.hash)

                  send(MessageKeys.CloseBlock,
                    DistributeClose(
                      Seq(sig),
                      BlockId(newLastBlock.height, newLastBlock.numTxs)),
                    currentQuorum.members)

                  log.info(
                    s"Block ${newLastBlock.height} successfully saved with ${newLastBlock.numTxs} txs sending to {}", currentQuorum.members)

                  stopIfConfirmed(0)

                case Failure(e) =>
                  log.error("FAILED TO CLOSE BLOCK! {} {}", height, e)
                  systemPanic()
              }
            case None =>
              log.info("Cannot close block {}, previous not yet closed ... retrying ", height)
              import context.dispatcher
              context.system.scheduler.scheduleOnce(1 second, self, height)
          }
        case Some(header) =>
          log.info("BlockHeader {} already exists, we are redistributing.",
                   header)
      }
  }

  private def stopIfConfirmed(numConfirms: Int) = {
    //TODO put a time limit on when this should end, can't accept sigs' indefinitely.
    if (q.minConfirms <= numConfirms) {

      log.debug(
        "stop block close distributor height:{} minconfirms: {} numconfirms: {}",
        height, q.minConfirms, numConfirms
      )

      messageEventBus.unsubscribe(self)
      context stop self
    }
  }

  private def updateQuorum: Receive = {
    case quorum: Quorum =>
      currentQuorum = quorum
  }

  override def receive: Receive = updateQuorum orElse waitForClose

}
