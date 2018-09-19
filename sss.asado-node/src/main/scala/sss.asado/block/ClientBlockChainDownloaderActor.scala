package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.common.block._
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.actor.AsadoEventPublishingActor
import sss.asado.block.signature.BlockSignatures
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.ledger.Ledgers
import sss.asado.network.{Connection, MessageEventBus, NetworkRef, SerializedMessage}
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
                                       nc: NetworkRef,
                                       messageRouter: MessageEventBus,
                                       stateMachine: ActorRef,
                                       numBlocksCached: Int,
                                       secondsBetweenChecks: Int,
                                       bc: BlockChain with BlockChainSignatures)(implicit db: Db, ledgers: Ledgers, chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging
    with AsadoEventPublishingActor {

  private case class CommitBlock(cId: GlobalChainIdMask, serverRef: ActorRef,
                                 blockId: BlockId,
                                 retryCount: Int = 0)

  messageRouter.subscribe(SimplePagedTx)
  messageRouter.subscribe(SimpleEndPageTx)
  messageRouter.subscribe(SimpleCloseBlock)
  messageRouter.subscribe(SimpleGetPageTxEnd)

  log.info("ClientBlockChainDownloaderActor actor has started...")

  override def postStop =
    log.warning("ClientBlockChainDownloaderActor is down!"); super.postStop

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
        val blockStorage = bc.block(lb.height + 1)
        val indexOfLastRow = blockStorage.maxMonotonicCommittedIndex
        val startAtNextIndex = indexOfLastRow + 1
        GetTxPage(lb.height + 1, startAtNextIndex, 50)
      }

      nc.send(
        SerializedMessage(SerializedMessage.noChain,
          MessageKeys.SimpleGetPageTx, getTxs.toBytes
        ),
        connectionToSyncWith.nodeId
      )


    case SerializedMessage(cId , MessageKeys.SimplePagedTx, bytes) =>
      decode(SimplePagedTx, bytes.toBlockChainTx) { blockChainTx =>
        Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
          case Failure(e) =>
            log.error(e,
                      s"Ledger cannot sync PagedTx, game over man, game over.")
          case Success(txDbId) => log.debug(s"CONFIRMED Up to $txDbId")
        }
      }

    case SerializedMessage(cId , MessageKeys.SimpleEndPageTx, bytes) =>
      sender() ! SerializedMessage(cId, MessageKeys.SimpleGetPageTx, bytes)

    case CommitBlock(cId, serverRef, blockId, reTryCount) => {

      Try(BlockChainLedger(blockId.blockHeight).commit(blockId)) match {
        case Failure(e) =>
          val retryDelaySeconds = if (reTryCount > 60) 60 else reTryCount + 1
          log.error(
            e,
            s"Could not commit this block ${blockId}, retry count is $reTryCount")
          context.system.scheduler.scheduleOnce(
            retryDelaySeconds seconds,
            self,
            CommitBlock(cId, serverRef, blockId, reTryCount + 1))
        case Success(_) =>
          Try(bc.closeBlock(bc.blockHeader(blockId.blockHeight - 1))) match {
            case Failure(e) =>
              log.error(
                e,
                s"Ledger cannot sync close block , game over man, game over.")
            case Success(blockHeader) =>
              publish(BlockClosedEvent(blockHeader.height))

              log.info(
                s"Client syncing - committed block height ${blockHeader.height}, num txs  ${blockHeader.numTxs}")
              assert(blockHeader.height == blockId.blockHeight,
                     s"(C)How can ${blockHeader} differ from ${blockId}")
              val nextBlockPage = GetTxPage(blockId.blockHeight + 1, 0)
              serverRef ! SerializedMessage(cId, MessageKeys.SimpleGetPageTx,
                                         nextBlockPage.toBytes)
              val blockHeightToDrop = blockId.blockHeight - numBlocksCached
              if (blockHeightToDrop > 0)
                Block.drop(blockId.blockHeight - numBlocksCached)
          }
      }
    }

    case SerializedMessage(cId, SimpleCloseBlock, bytes) =>
      decode(SimpleCloseBlock, bytes.toDistributeClose) { distClose =>
        val blockSignaturePersistence =
          BlockSignatures(distClose.blockId.blockHeight)
        blockSignaturePersistence.write(distClose.blockSigs)
        self ! CommitBlock(cId, sender(), distClose.blockId)
      }

    case SerializedMessage(cId, SimpleGetPageTxEnd, getTxPage) =>
      stateMachine ! ClientSynced
      log.info(
        s"Client downloader is synced, pausing for $secondsBetweenChecks seconds")
      context.system.scheduler.scheduleOnce(secondsBetweenChecks seconds) { nc.send(
          SerializedMessage(cId,
            MessageKeys.SimpleGetPageTx, getTxPage
          ), connectionToSyncWith.nodeId
        )
      }

  }

}
