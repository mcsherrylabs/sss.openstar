package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props, ReceiveTimeout, Terminated}
import sss.asado.common.block._
import sss.asado.actor.AsadoEventSubscribedActor


import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumMonitor.Quorum

import sss.asado.chains.TxDistributorActor.{TxConfirmed, TxReplicated}
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
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
  case class TxConfirmed(bTx: BlockChainTx, confirmers: Set[UniqueNodeIdentifier])

  case class CheckedProp(value:Props)

  def props(bTx: BlockChainTx,
            q: Quorum,
            messageEventBus: MessageEventBus,
            send: NetSendToMany
           )
           (implicit db: Db, chainId: GlobalChainIdMask): CheckedProp =
    CheckedProp(Props(classOf[TxDistributeeActor], bTx, q))


  def apply(p:CheckedProp)(implicit context: ActorContext): ActorRef = {
    context.actorOf(p.value)
  }
}

private class TxDistributorActor(bTx: BlockChainTx,
                                 q: Quorum,
                                 messageEventBus: MessageEventBus,
                                 send: NetSendToMany
                    )(implicit db: Db, chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with AsadoEventSubscribedActor {

  self ! q


  private var confirms: Set[UniqueNodeIdentifier] = Set()
  private var badConfirms: Set[UniqueNodeIdentifier] = Set()

  messageEventBus.subscribe(classOf[Quorum])
  messageEventBus.subscribe(MessageKeys.AckConfirmTx)
  messageEventBus.subscribe(MessageKeys.NackConfirmTx)

  log.info("TxDistributor actor has started...")

  private def finishSendingConfirms(q: Quorum) : Receive = {
    case IncomingMessage(`chainId`, MessageKeys.AckConfirmTx, mem, _) =>
      confirms += mem
      send(SerializedMessage(MessageKeys.ConfirmTx, bTx), Set(mem))

      if(confirms.size + badConfirms.size == q.members.size)
        context stop self
  }

  private def withQuorum(q: Quorum): Receive = onQuorum orElse {

    case TxConfirmed(bTx, confirmers) =>
      send(SerializedMessage(MessageKeys.ConfirmTx, bTx), confirmers)

    case IncomingMessage(`chainId`, MessageKeys.AckConfirmTx, mem, _) =>
      confirms += mem
      if(confirms.size >= q.minConfirms) {
        context.parent ! TxReplicated(bTx, confirms)
        context become finishSendingConfirms(q)
      }

    case IncomingMessage(`chainId`, MessageKeys.NackConfirmTx, mem, bTx) =>
      badConfirms += mem
      log.warning("{} could not confirm a tx! {}", mem, bTx)
  }

  private def onQuorum: Receive = {

    case quorum: Quorum =>
      context become withQuorum(quorum)
      val remainingMembers = q.members.filterNot(confirms.contains(_))
      send(SerializedMessage(MessageKeys.DistributeTx, bTx), remainingMembers)

  }

  override def receive: Receive = onQuorum
}
