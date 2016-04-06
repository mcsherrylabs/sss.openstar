package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import ledger._
import sss.asado.MessageKeys
import sss.asado.ledger.{Ledger, UTXOLedger}
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol.SyncWithLeader
import sss.asado.storage.TxDBStorage
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


class BlockChainDownloaderActor(utxo: UTXOLedger, nc: ActorRef, messageRouter: ActorRef, bc: BlockChain)(implicit db: Db) extends Actor with ActorLogging {

  private case class PuntedConfirm(ref :ActorRef, confirmTx: ConfirmTx)

  messageRouter ! Register(MessageKeys.PagedTx)
  messageRouter ! Register(MessageKeys.EndPageTx)
  messageRouter ! Register(MessageKeys.ConfirmTx)
  messageRouter ! Register(MessageKeys.ReConfirmTx)
  messageRouter ! Register(MessageKeys.CloseBlock)
  messageRouter ! Register(MessageKeys.Synced)

  private def makeNextLedger(lastHeight: Long) = new Ledger(lastHeight + 1, TxDBStorage(lastHeight+ 1), utxo)

  override def postStop = log.warning("BlockChainDownloaderActor Monitor actor is down!"); super.postStop

  override def receive: Receive = {
    val lastBlock = bc.lastBlock
    syncLedgerWithLeader(lastBlock, makeNextLedger(lastBlock.height))
  }

  var synced = false

  private def syncLedgerWithLeader(lastBlock: BlockHeader, ledger: Ledger): Receive = {

    case SyncWithLeader(leader) =>
        val getTxs = {
            val lb = lastBlock
            val blockStorage = TxDBStorage(lb.height + 1)
            val lastIndexOfRow = blockStorage.count
            GetTxPage(lb.height + 1, lastIndexOfRow)
        }

        nc ! SendToNetwork(NetworkMessage(MessageKeys.GetTxPage, getTxs.toBytes), (_ filter(_.nodeId.id == leader)) )


    case NetworkMessage(MessageKeys.PagedTx, bytes) =>
      val pagedTx = bytes.toSignedTx
      ledger(pagedTx) match {
        case Failure(e) => log.error(s"Ledger cannot sync, game over man, game over.", e)
        case Success(txDbId) =>
          log.debug(s"CONFIRMED Up to $txDbId")
      }


    case NetworkMessage(MessageKeys.EndPageTx, bytes) =>
      val getTxPage = bytes.toGetTxPage
      val nextPage = GetTxPage(getTxPage.blockHeight, getTxPage.index + getTxPage.pageSize, getTxPage.pageSize)
      sender() ! NetworkMessage(MessageKeys.GetTxPage, nextPage.toBytes)

    case NetworkMessage(MessageKeys.CloseBlock, _) =>
      //close block
      bc.closeBlock(lastBlock) match {
        case Failure(e) => log.error(s"Ledger cannot sync, game over man, game over.", e)
        case Success(blockHeader) =>
          log.info(s"Synching - block height ${blockHeader.height}")
          context.become(syncLedgerWithLeader(blockHeader, makeNextLedger(blockHeader.height)))
          if(!synced) {
            val nextBlockPage = GetTxPage(blockHeader.height + 1, 0)
            // TODO SIGN THE BLOCK
            sender() ! NetworkMessage(MessageKeys.GetTxPage, nextBlockPage.toBytes)
          }
      }

    case NetworkMessage(MessageKeys.Synced, _) => synced = true; log.info("Downloader is synced")

    case NetworkMessage(MessageKeys.ReConfirmTx, bytes) =>
      Try(bytes.toConfirmTx) match {
        case Failure(e) => log.error(e, "Unable to decoode a request for confirmation")
        case Success(confirmStx) =>
          TxDBStorage(confirmStx.height).get(confirmStx.stx.txId) match {
            case None => sender() ! NetworkMessage(MessageKeys.NackConfirmTx, Array())
            case Some(sTx) =>
              sender() ! NetworkMessage(MessageKeys.AckConfirmTx, AckConfirmTx(confirmStx.stx.txId, confirmStx.height).toBytes)
          }
      }

    case p @ PuntedConfirm(client, confirmStx) => {
      if(ledger.blockHeight < confirmStx.height) {
        context.system.scheduler.scheduleOnce(1 milliseconds, self, p)
      } else {
        ledger(confirmStx.stx) match {
          case Failure(e) =>
            client ! NetworkMessage(MessageKeys.NackConfirmTx, confirmStx.stx.txId)
            log.error(s"Ledger cannot sync, game over man, game over.", e)
          case Success(txDbId) =>
            if(confirmStx.height !=  txDbId.height) {
              log.info(s"Local id is ${txDbId}, but remote id is ${confirmStx.height}")
            }
            client ! NetworkMessage(MessageKeys.AckConfirmTx, AckConfirmTx(confirmStx.stx.txId, txDbId.height).toBytes)
        }
      }
    }

    case NetworkMessage(MessageKeys.ConfirmTx, bytes) =>
      Try(bytes.toConfirmTx) match {
        case Failure(e) => log.error(e, "Unable to decoode a request for confirmation")
        case Success(confirmStx) if(ledger.blockHeight < confirmStx.height) =>
          val sndr = sender()
          context.system.scheduler.scheduleOnce(1 milliseconds, self, PuntedConfirm(sndr, confirmStx))
        case Success(confirmStx) if(ledger.blockHeight == confirmStx.height) =>
          ledger(confirmStx.stx) match {
            case Failure(e) =>
              sender() ! NetworkMessage(MessageKeys.NackConfirmTx, confirmStx.stx.txId)
              log.error(s"Ledger cannot sync, game over man, game over.", e)
            case Success(txDbId) =>
              if(confirmStx.height !=  txDbId.height) {
                log.info(s"Local id is ${txDbId}, but remote id is ${confirmStx.height}")
              }
              sender() ! NetworkMessage(MessageKeys.AckConfirmTx, AckConfirmTx(confirmStx.stx.txId, txDbId.height).toBytes)
          }
        case Success(confirmStx) => log.error(s"Local block height is ${ledger.blockHeight}, but remote is ${confirmStx.height}")
      }
  }

}
