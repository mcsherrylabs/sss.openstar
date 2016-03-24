package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import com.twitter.util.LruMap
import sss.asado.MessageKeys
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkMessage
import sss.asado.storage.TxDBStorage
import sss.db.Db

import scala.util.{Failure, Success, Try}

class BlockChainMonitorActor(messageRouter: ActorRef)(implicit db: Db) extends Actor with ActorLogging {

  messageRouter ! Register(MessageKeys.ConfirmTx)

  private val lru = new LruMap[Long, TxDBStorage](3)

  override def postStop = log.warning("BlockChain Monitor actor is down!"); super.postStop

  private def writeToDb(confirm: ConfirmTx): Try[Long] = Try {
    lru.getOrElseUpdate(confirm.height, TxDBStorage(confirm.height)).write(confirm.stx.txId, confirm.stx)
  }

  // there must be a last closed block or we cannot start up.
  override def receive: Receive = {

    case NetworkMessage(MessageKeys.ConfirmTx, bytes) =>
      Try(bytes.toConfirmTx) match {
        case Failure(e) => log.error(e, "Unable to decoode a request for confirmation")
        case Success(confirm) =>
          writeToDb(confirm) map(localId => {
            log.info(s"Local id is $localId, remote id is ${confirm.id}")
            sender() ! NetworkMessage(MessageKeys.AckConfirmTx, AckConfirmTx(confirm.stx.txId, confirm.height, confirm.id).toBytes)
          })
      }

  }

}
