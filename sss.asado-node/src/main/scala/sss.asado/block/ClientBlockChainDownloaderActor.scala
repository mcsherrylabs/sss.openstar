package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import sss.asado.MessageKeys
import sss.asado.MessageKeys._

import sss.asado.block.signature.BlockSignatures

import sss.asado.ledger.Ledgers
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoStateProtocol.{ClientSynced}
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


case object SynchroniseWithConn

/**
  * This actor's job is to bring the local blockchain up to date with
  * the home nodes blockchain and keep it there.
  *
  */
class ClientBlockChainDownloaderActor(
                                nc: ActorRef,
                                messageRouter: ActorRef,
                                stateMachine: ActorRef,
                                numBlocksCached: Int,
                                bc: BlockChain with BlockChainSignatures)
                               (implicit db: Db, ledgers: Ledgers) extends Actor with ActorLogging {


  private case class CommitBlock(serverRef: ActorRef,  blockId: BlockId, retryCount: Int = 0)

  messageRouter ! Register(PagedTx)
  messageRouter ! Register(EndPageTx)
  messageRouter ! Register(CloseBlock)
  messageRouter ! Register(SimpleGetPageTxEnd)


  log.info("ClientBlockChainDownloaderActor actor has started...")

  override def postStop = log.warning("ClientBlockChainDownloaderActor is down!"); super.postStop

  override def receive: Receive = init

  private def init: Receive = {
    case SynchroniseWith(who) =>
      context.become(init.orElse(syncLedgerWithLeader(who)))
      self ! SynchroniseWithConn
  }

  private def syncLedgerWithLeader(connectionToSyncWith: Connection): Receive = {

    case SynchroniseWithConn =>

        val getTxs = {
            val lb = bc.lastBlockHeader
            val blockStorage = Block(lb.height + 1)
            val indexOfLastRow = blockStorage.maxMonotonicCommittedIndex
            val startAtNextIndex = indexOfLastRow + 1
            GetTxPage(lb.height + 1, startAtNextIndex, 50)
        }

        nc ! SendToNetwork(NetworkMessage(MessageKeys.SimpleGetPageTx, getTxs.toBytes), _ => Set(connectionToSyncWith) )


    case NetworkMessage(MessageKeys.PagedTx, bytes) =>
      decode(PagedTx, bytes.toBlockChainTx) { blockChainTx =>
        Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
          case Failure(e) => log.error(e, s"Ledger cannot sync PagedTx, game over man, game over.")
          case Success(txDbId) => log.debug(s"CONFIRMED Up to $txDbId")
        }
      }


    case NetworkMessage(MessageKeys.EndPageTx, bytes) => sender() ! NetworkMessage(MessageKeys.SimpleGetPageTx, bytes)

    case CommitBlock(serverRef, blockId, reTryCount) => {

      Try(BlockChainLedger(blockId.blockHeight).commit(blockId)) match {
        case Failure(e) =>
          val retryDelaySeconds = if(reTryCount > 60) 60 else reTryCount + 1
          log.error(e, s"Could not commit this block ${blockId}, retry count is $reTryCount")
          context.system.scheduler.scheduleOnce(retryDelaySeconds seconds, self, CommitBlock(serverRef, blockId, reTryCount + 1))
        case Success(_) =>
          Try(bc.closeBlock(bc.blockHeader(blockId.blockHeight - 1))) match {
            case Failure(e) => log.error(e, s"Ledger cannot sync close block , game over man, game over.")
            case Success(blockHeader) =>
              log.info(s"Client syncing - committed block height ${blockHeader.height}, num txs  ${blockHeader.numTxs}")
              val nextBlockPage = GetTxPage(blockHeader.height + 1, 0)
              serverRef ! NetworkMessage(MessageKeys.SimpleGetPageTx, nextBlockPage.toBytes)
              Block.drop(blockId.blockHeight - numBlocksCached)
          }
      }
    }

    case NetworkMessage(CloseBlock, bytes) =>
      decode(CloseBlock, bytes.toDistributeClose) { distClose =>
        val blockSignaturePersistence = BlockSignatures(distClose.blockId.blockHeight)
        distClose.blockSigs.foreach { sig =>
          blockSignaturePersistence.write(sig)
        }
        self ! CommitBlock(sender(), distClose.blockId)
      }

    case NetworkMessage(SimpleGetPageTxEnd, _) =>
      stateMachine ! ClientSynced
      log.info(s"Client downloader is synced pausing for 20 seconds")
      context.system.scheduler.scheduleOnce(20 seconds, self, SynchroniseWithConn)

  }


}
