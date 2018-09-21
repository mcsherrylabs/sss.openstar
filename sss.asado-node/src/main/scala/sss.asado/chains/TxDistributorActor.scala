package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props, SupervisorStrategy}
import sss.asado.common.block._
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumMonitor.{Quorum, QuorumLost}
import sss.asado.chains.TxDistributorActor.{TxCommitted, TxReplicated}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.db.Db

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

  case class TxReplicated(bTx: BlockChainTx, confirmers: Set[UniqueNodeIdentifier])
  case class TxCommitted(bTx: BlockChainTxId, confirmers: Set[UniqueNodeIdentifier])

  case class CheckedProp(value:Props)

  def props(bTx: BlockChainTx,
            q: Quorum,
           )
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageEventBus: MessageEventBus
            ): CheckedProp =

    CheckedProp(Props(classOf[TxDistributorActor], bTx, q, db, chainId, messageEventBus, send))


  def apply(p:CheckedProp)(implicit context: ActorContext): ActorRef = {
    context.actorOf(p.value)
  }
}

private class TxDistributorActor(bTx: BlockChainTx,
                                 q: Quorum,

                    )(implicit db: Db,
                      chainId: GlobalChainIdMask,
                      messageEventBus: MessageEventBus,
                      send: Send)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with AsadoEventSubscribedActor {

  self ! q

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private var confirms: Set[UniqueNodeIdentifier] = Set()
  private var badConfirms: Set[UniqueNodeIdentifier] = Set()

  messageEventBus.subscribe(classOf[QuorumLost])
  messageEventBus.subscribe(classOf[Quorum])
  messageEventBus.subscribe(MessageKeys.AckConfirmTx)
  messageEventBus.subscribe(MessageKeys.NackConfirmTx)

  log.info("TxDistributor actor has started...")

  override def postStop(): Unit = { log.debug("TxDistributor {} actor stopped ", bTx.toId)}

  private def finishSendingCommits(q: Quorum) : Receive = {
    case IncomingMessage(`chainId`, MessageKeys.AckConfirmTx, mem, _) =>
      confirms += mem
      send(MessageKeys.CommittedTx, bTx.toId, mem)

      if(confirms.size + badConfirms.size == q.members.size)
        context stop self
  }

  private def withQuorum(q: Quorum): Receive = onQuorum orElse {

    case TxCommitted(bTxId, confirmers) =>
      context become finishSendingCommits(q)
      send(MessageKeys.CommittedTx, bTxId, confirmers)
      if(confirms.size + badConfirms.size == q.members.size)
        context stop self

    case IncomingMessage(`chainId`, MessageKeys.AckConfirmTx, mem, _) =>
      confirms += mem
      if(confirms.size >= q.minConfirms) {
        context.parent ! TxReplicated(bTx, confirms)
      }

    case IncomingMessage(`chainId`, MessageKeys.NackConfirmTx, mem, bTx) =>
      badConfirms += mem
      log.warning("{} could not confirm a tx! {}", mem, bTx)
  }

  private def onQuorum: Receive = {

    case QuorumLost(leader) =>
      log.info("QuorumLost ({}) during Distribution of tx {}", leader, bTx)
      context stop self

    case quorum: Quorum =>
      val remainingMembers = q.members.filterNot(confirms.contains(_))
      if(remainingMembers.isEmpty) {
        // don't need more confirms, we are done!
        context.parent ! TxReplicated(bTx, remainingMembers)
        context become withQuorum(quorum)
      } else {
        context become withQuorum(quorum)
        send(MessageKeys.ConfirmTx, bTx, remainingMembers)
      }

  }

  override def receive: Receive = onQuorum
}
