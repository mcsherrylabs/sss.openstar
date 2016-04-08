package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import ledger._
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol.SyncWithLeader
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


class BlockChainDownloaderActor(nc: ActorRef, messageRouter: ActorRef, bc: BlockChain)(implicit db: Db) extends Actor with ActorLogging {


  private case class CommitBlock(serverRef: ActorRef,  blockId: BlockId, retryCount: Int = 0)

  messageRouter ! Register(PagedTx)
  messageRouter ! Register(EndPageTx)
  messageRouter ! Register(ConfirmTx)
  messageRouter ! Register(ReConfirmTx)
  messageRouter ! Register(CloseBlock)
  messageRouter ! Register(Synced)

  private def makeNextLedger(lastHeight: Long) = BlockChainLedger(lastHeight + 1)

  override def postStop = log.warning("BlockChainDownloaderActor Monitor actor is down!"); super.postStop

  override def receive: Receive = syncLedgerWithLeader


  var synced = false

  private def syncLedgerWithLeader: Receive = {

    case SyncWithLeader(leader) =>
        val getTxs = {
            val lb = bc.lastBlock
            val blockStorage = Block(lb.height + 1)
            val lastIndexOfRow = blockStorage.maxMonotonicIndex
            GetTxPage(lb.height + 1, lastIndexOfRow)
        }

        nc ! SendToNetwork(NetworkMessage(MessageKeys.GetPageTx, getTxs.toBytes), (_ filter(_.nodeId.id == leader)) )


    case NetworkMessage(MessageKeys.PagedTx, bytes) =>
      decode(PagedTx, bytes.toBlockChainTx) { blockChainTx =>
        Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
          case Failure(e) => log.error(e, s"Ledger cannot sync PagedTx, game over man, game over.")

          case Success(txDbId) =>
            log.debug(s"CONFIRMED Up to $txDbId")
        }
      }


    case NetworkMessage(MessageKeys.EndPageTx, bytes) =>
      val getTxPage = bytes.toGetTxPage
      val nextPage = GetTxPage(getTxPage.blockHeight, getTxPage.index + getTxPage.pageSize, getTxPage.pageSize)
      sender() ! NetworkMessage(MessageKeys.GetPageTx, nextPage.toBytes)

    case CommitBlock(serverRef, blockId, reTryCount) if reTryCount > 5 => log.error(s"Ledger cannot sync retry")

    case CommitBlock(serverRef, blockId, reTryCount) => {

      Try(BlockChainLedger(blockId.blockHeight).commit(blockId)) match {
        case Failure(e) =>
          log.error(e, s"Could not commit this block ${blockId}", e)
          context.system.scheduler.scheduleOnce(500 millis, self, CommitBlock(serverRef, blockId, reTryCount + 1))
        case Success(_) =>
          Try(bc.closeBlock(bc.blockHeader(blockId.blockHeight - 1))) match {
            case Failure(e) => log.error(e, s"Ledger cannot sync close block , game over man, game over.")
            case Success(blockHeader) =>
              log.info(s"Synching - block height ${blockHeader.height}")

              if (!synced) {
                val nextBlockPage = GetTxPage(blockHeader.height + 1, 0)
                // TODO SIGN THE BLOCK
                serverRef ! NetworkMessage(MessageKeys.GetPageTx, nextBlockPage.toBytes)
              }
          }
      }
    }

    case NetworkMessage(CloseBlock, bytes) =>
      val sendr = sender()
      decode(CloseBlock, bytes.toBlockId)(blockId => self ! CommitBlock(sendr, blockId))


    case NetworkMessage(Synced, _) => synced = true; log.info("Downloader is synced")


    case NetworkMessage(ReConfirmTx, bytes) =>
      log.error("RECONFIRM IS THE SAME AS CONFIRM????")
      //context.system.scheduler.scheduleOnce(1 milliseconds, self, _)


    case NetworkMessage(ConfirmTx, bytes) =>
      decode(ConfirmTx, bytes.toBlockChainTx) { blockChainTx =>
          Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
            case Failure(e) =>
              sender() ! NetworkMessage(MessageKeys.NackConfirmTx, blockChainTx.toId.toBytes)
              log.error(e, s"Ledger cannot sync, game over man, game over.")
            case Success(resultBlockChainTx) =>
              log.info(s"Local index is ${resultBlockChainTx.toId.blockTxId.index}, remote index is ${blockChainTx.toId.blockTxId.index}")
              val isSame = ByteArrayComparison(blockChainTx.toId.blockTxId.txId).isSame(resultBlockChainTx.toId.blockTxId.txId)
              log.info(s"Txs same?  $isSame")
              log.info(s"Local is ${resultBlockChainTx.height}, remote id is ${blockChainTx.height}")
              sender() ! NetworkMessage(MessageKeys.AckConfirmTx, resultBlockChainTx.toId.toBytes)
          }
      }
  }


}
