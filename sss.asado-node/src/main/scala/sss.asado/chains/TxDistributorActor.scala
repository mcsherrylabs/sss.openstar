package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Cancellable, Props, SupervisorStrategy}
import sss.asado.common.block._
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumFollowersSyncedMonitor.SyncedQuorum
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.chains.TxDistributorActor._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.MessageEventBus.EventSubscriptionsByteOps
import sss.asado.network.MessageEventBus.EventSubscriptionsClassOps
import sss.asado.network._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db

import concurrent.duration._
import scala.language.postfixOps

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

object TxDistributorActor {

  sealed trait TxNack {
    val bTx: BlockChainTxId
    val rejectors: Set[UniqueNodeIdentifier]
  }

  case class TxNackReplicated(bTx: BlockChainTxId, rejectors: Set[UniqueNodeIdentifier]) extends TxNack
  case class TxReplicated(bTx: BlockChainTx)
  case class TxRejected(bTx: BlockChainTxId, rejectors: Set[UniqueNodeIdentifier])
  case class TxCommitted(bTx: BlockChainTxId)
  case class TxTimeout(bTx: BlockChainTxId, rejectors: Set[UniqueNodeIdentifier]) extends TxNack
  private case class InternalTxTimeout(bTx: BlockChainTxId)

  case class CheckedProps(value:Props, name:String)

  def props(bTx: BlockChainTx)
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageEventBus: MessageEventBus
            ): CheckedProps =

    CheckedProps(Props(classOf[TxDistributorActor], bTx, db, chainId, messageEventBus, send),
      s"TxDistributorActor_${chainId}_${bTx.height}_${bTx.blockTx.index}")


  def apply(p:CheckedProps)(implicit context: ActorContext): ActorRef = {
    context.actorOf(p.value, p.name)
  }
}

private class TxDistributorActor(bTx: BlockChainTx
                    )(implicit db: Db,
                      chainId: GlobalChainIdMask,
                      messageEventBus: MessageEventBus,
                      send: Send)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with AsadoEventSubscribedActor {


  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private var confirms: Set[UniqueNodeIdentifier] = Set()
  private var badConfirms: Set[UniqueNodeIdentifier] = Set()

  private var sendOnce = false

  private var txTimeoutTimer: Option[Cancellable] = None

  private val bTxId = bTx.toId

  Seq(classOf[QuorumLost]).subscribe
  Seq(MessageKeys.AckConfirmTx, MessageKeys.NackConfirmTx).subscribe


  override def postStop(): Unit = { log.debug("TxDistributor {} actor stopped ", bTx.toId)}

  private def finishSendingCommits(sq: SyncedQuorum) : Receive = {
    case IncomingMessage(`chainId`, MessageKeys.AckConfirmTx, member, `bTxId`) if(!confirms.contains(member)) =>
      confirms += member
      send(MessageKeys.CommittedTxId, bTxId, member)

      if(confirms.size + badConfirms.size == sq.members.size)
        messageEventBus unsubscribe self
        context stop self

  }

  private def withQuorum(sq: SyncedQuorum): Receive = onQuorum orElse {

    case InternalTxTimeout(`bTxId`) =>
      context stop self
      log.warning("TxTimeout for {}", bTxId)
      //TODO add sendOnce check
      context.parent ! TxTimeout(bTxId, badConfirms)

    case TxRejected(`bTxId`, _) =>
      log.warning("Quorum REJECTING {}", bTxId)
      send(MessageKeys.QuorumRejectedTx, bTxId, sq.members)
      txTimeoutTimer map (_.cancel())
      context stop self


    case TxCommitted(`bTxId`) =>
      log.info("Distributed TxCommitted {}", bTxId)
      send(MessageKeys.CommittedTxId, bTxId, confirms)
      txTimeoutTimer map (_.cancel())

      if(confirms.size + badConfirms.size == sq.members.size) {
        messageEventBus unsubscribe self
        context stop self
      } else
        context become finishSendingCommits(sq)


    case IncomingMessage(`chainId`, MessageKeys.AckConfirmTx, mem, `bTxId`) =>
      confirms += mem
      if(!sendOnce && confirms.size == sq.members.size) {
        log.info("Tx replicated confirms {} min confirms {}", confirms, sq.minConfirms)
        context.parent ! TxReplicated(bTx)
        sendOnce = true
      }

    case IncomingMessage(`chainId`, MessageKeys.NackConfirmTx, mem, `bTx`) =>
      badConfirms += mem
      if(!sendOnce && badConfirms.size == sq.members.size) {
        context.parent ! TxNackReplicated(bTxId, badConfirms)
        sendOnce = true
        messageEventBus unsubscribe self
        context stop self
        log.warning("{} could not confirm a tx! {}, quorum rejects this tx!", mem, bTx)
      } else log.warning("{} could not confirm a tx! {}", mem, bTx)

  }

  private def onQuorum: Receive = {

    case QuorumLost(`chainId`) =>
      log.info("QuorumLost during Distribution of tx {}",  bTx)
      txTimeoutTimer map (_.cancel())
      messageEventBus unsubscribe self
      context stop self

    case quorum: SyncedQuorum =>

      val remainingMembers = quorum.members.diff(confirms)
      log.info(s"Q members ${quorum.members}, remaining $remainingMembers")

      import context.dispatcher
      txTimeoutTimer map (_.cancel())

      if(remainingMembers.isEmpty) {
        // don't need more confirms, we are done!
        if(!sendOnce) {
          context.parent ! TxReplicated(bTx)
          sendOnce = true
        }
        context become withQuorum(quorum)
      } else {
        txTimeoutTimer = Option(context.system.scheduler.scheduleOnce(10 seconds,
          self,
          InternalTxTimeout(bTxId)
        )
        )
        context become withQuorum(quorum)
        send(MessageKeys.ConfirmTx, bTx, remainingMembers)
      }

  }

  override def receive: Receive = onQuorum
}
