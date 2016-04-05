package sss.asado.state

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.agent.Agent
import sss.asado.MessageKeys
import sss.asado.network.Connection
import sss.asado.network.MessageRouter.{Register, UnRegister}

/**
  * Created by alan on 4/1/16.
  */
case class Forward(leader: String)
case object CancelForward

class TxForwarderActor(thisNodeId: String,
                       connectedPeers: Agent[Set[Connection]],
                       messageRouter: ActorRef
                             ) extends Actor with ActorLogging {


  private def noForward: Receive = {
    case Forward(leader) =>
      messageRouter ! Register(MessageKeys.SignedTx)
      messageRouter ! Register(MessageKeys.SeqSignedTx)
      messageRouter ! Register(MessageKeys.SignedTxAck)
      messageRouter ! Register(MessageKeys.SignedTxNack)
      messageRouter ! Register(MessageKeys.AckConfirmTx)

      connectedPeers().find(conn => conn.nodeId.id == leader) match {
        case None => log.error(s"Cannot forward to leader $leader, connection not found.")
        case Some(conn) =>
          context watch conn.handlerRef
          context.become(forwardMode(conn.handlerRef))
      }

  }
  private def forwardMode(leaderRef: ActorRef): Receive = {

    case Terminated(leaderRef) => self ! CancelForward

    case CancelForward =>
      messageRouter ! UnRegister(MessageKeys.SignedTx)
      messageRouter ! UnRegister(MessageKeys.SeqSignedTx)
      messageRouter ! UnRegister(MessageKeys.SignedTxAck)
      messageRouter ! UnRegister(MessageKeys.SignedTxNack)
      messageRouter ! UnRegister(MessageKeys.AckConfirmTx)
      context.become(noForward)
  }

  final override def receive = noForward

}
