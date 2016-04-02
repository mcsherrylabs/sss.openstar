package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.agent.Agent
import block._
import sss.asado.MessageKeys
import sss.asado.network.MessageRouter.Register
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.storage.TxDBStorage
import sss.db.Db

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 3/24/16.
  */
class WriteConfirmationActor(peers: Agent[Set[Connection]], messageRouter: ActorRef)(implicit db: Db) extends Actor with ActorLogging {

  messageRouter ! Register(MessageKeys.AckConfirmTx)

  private case class ClientTx(client : ActorRef, height: Long, id: Long)

  private def awaitConfirms(awaitGroup: Map[ActorRef, List[ClientTx]]): Receive = {
    case DistributeTx(client, signedTx, height, id) => {
      val allPeers: Set[Connection] = peers()

      def toMapElement(cp: Connection) = {
        cp.handlerRef ! NetworkMessage(MessageKeys.ConfirmTx, ConfirmTx(signedTx, height, id).toBytes)
        cp.handlerRef -> (awaitGroup(cp.handlerRef) :+ ClientTx(client, height, id))
      }

      context.become(awaitConfirms(allPeers.map(toMapElement).toMap.withDefaultValue(Nil)))
    }

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

        //TODO Add reaper to remove old sndr's that have died during wait
        context.become(awaitConfirms(newMap.withDefaultValue(Nil)))
      } match {
        case Failure(e) => log.error(e, "Didn't handle confirm correctly.")
        case Success(_) =>
      }
  }

  private def addConfirmation(confirm: AckConfirmTx) = TxDBStorage.confirm(confirm.height, confirm.id)

  override def receive: Receive = awaitConfirms(Map.empty[ActorRef, List[ClientTx]].withDefaultValue(Nil))
}
