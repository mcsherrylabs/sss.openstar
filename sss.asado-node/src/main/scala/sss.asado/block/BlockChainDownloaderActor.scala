package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import org.joda.time.DateTime
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.account.NodeIdentity
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.ledger.Ledgers
import sss.asado.network.MessageRouter.{Register, UnRegister}
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.{Connection, NetworkMessage}
import sss.asado.state.AsadoStateProtocol
import sss.asado.state.AsadoStateProtocol.{Synced => _, _}
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


case class SynchroniseWith(who: Connection)

/**
  * This actor's job is to bring the local blockchain up to date with
  * the leaders blockchain and keep it there.
  *
  * When it's asked to SynchroniseWith with 'who' it pages down the
  * tx's and the block sig's and closes the blocks (commiting the tx's to
  * the ledger in the process) until it gets 'Synced'
  *
  * It raises the 'Synced' event to the stateMachine.
  *
  * Then it continues to download tx's more slowly through the 'ConfimTx' message
  * but continues to close blocks through the 'CloseBlock' message
  *
  * @param nodeIdentity
  * @param nc
  * @param messageRouter
  * @param stateMachine
  * @param bc
  * @param db
  * @param ledgers
  */
class BlockChainDownloaderActor(nodeIdentity: NodeIdentity,
                                nc: ActorRef,
                                messageRouter: ActorRef,
                                stateMachine: ActorRef,
                                bc: BlockChain with BlockChainSignatures)
                               (implicit db: Db, ledgers: Ledgers) extends Actor with ActorLogging {


  private case class CommitBlock(serverRef: ActorRef,  blockId: BlockId, retryCount: Int = 0)

  messageRouter ! Register(Synced)
  messageRouter ! Register(PagedTx)
  messageRouter ! Register(EndPageTx)
  messageRouter ! Register(CloseBlock)
  messageRouter ! Register(BlockSig)
  stateMachine ! RegisterStateEvents


  log.info(s"BlockChainDownloader actor has started... $self")

  override def postStop = log.warning("BlockChainDownloaderActor is down!"); super.postStop

  override def receive: Receive = syncLedgerWithLeader

  private var synced = false

  private def syncLedgerWithLeader: Receive = {

    case LocalLeaderEvent =>
      messageRouter ! UnRegister(ConfirmTx)


    case RemoteLeaderEvent(conn) =>
      messageRouter ! Register(ConfirmTx)


      context.system.scheduler.scheduleOnce(1 seconds, self, SynchroniseWith(conn))


    case SynchroniseWith(who) =>
        val getTxs = {
            val lb = bc.lastBlockHeader
            val blockStorage = Block(lb.height + 1)
            val indexOfLastRow = blockStorage.maxMonotonicCommittedIndex
            val startAtNextIndex = indexOfLastRow + 1
            GetTxPage(lb.height + 1, startAtNextIndex, 50)
        }

        nc ! SendToNetwork(NetworkMessage(MessageKeys.GetPageTx, getTxs.toBytes), _ => Set(who) )


    case NetworkMessage(MessageKeys.PagedTx, bytes) =>
      decode(PagedTx, bytes.toBlockChainTx) { blockChainTx =>
        Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
          case Failure(e) => log.error(e, s"Ledger cannot sync PagedTx, game over man, game over.")
          case Success(txDbId) => log.debug(s"CONFIRMED Up to $txDbId")
        }
      }


    case NetworkMessage(MessageKeys.EndPageTx, bytes) => sender() ! NetworkMessage(MessageKeys.GetPageTx, bytes)

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
              log.info(s"Synching - committed block height ${blockHeader.height}, num txs  ${blockHeader.numTxs}")
              if(BlockSignatures(blockHeader.height).indexOfBlockSignature(nodeIdentity.id).isEmpty) {
                val sig = nodeIdentity.sign(blockHeader.hash)
                val newSig = BlockSignature(0, new DateTime(),
                  blockHeader.height,
                  nodeIdentity.id,
                  nodeIdentity.publicKey, sig)

                serverRef ! NetworkMessage(MessageKeys.BlockNewSig, newSig.toBytes)
              }
              assert(blockHeader.height == blockId.blockHeight, s"How can ${blockHeader} differ from ${blockId}")
              if (!synced) {
                val nextBlockPage = GetTxPage(blockHeader.height + 1, 0)
                serverRef ! NetworkMessage(MessageKeys.GetPageTx, nextBlockPage.toBytes)
              }
          }
      }
    }


    case NetworkMessage(BlockSig, bytes) =>
      decode(BlockSig, bytes.toBlockSignature) { blkSig =>
        BlockSignatures(blkSig.height).write(blkSig)
      }

    case NetworkMessage(CloseBlock, bytes) =>
      decode(CloseBlock, bytes.toDistributeClose) { distClose =>
        val blockSignaturePersistence = BlockSignatures(distClose.blockId.blockHeight)
        distClose.blockSigs.foreach { sig =>
          blockSignaturePersistence.write(sig)
        }
        self ! CommitBlock(sender(), distClose.blockId)
      }

    case NetworkMessage(Synced, bytes) =>
      synced = true
      val getTxPage = bytes.toGetTxPage
      stateMachine ! AsadoStateProtocol.Synced
      log.info(s"Downloader is synced to tx page $getTxPage")


    case NetworkMessage(ConfirmTx, bytes) =>
      decode(ConfirmTx, bytes.toBlockChainTx) { blockChainTx =>
          Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
            case Failure(e) =>
              sender() ! NetworkMessage(MessageKeys.NackConfirmTx, blockChainTx.toId.toBytes)
              log.error(e, s"Ledger cannot journal ${blockChainTx.blockTx}, game over.")
            case Success(resultBlockChainTx) =>
              if(resultBlockChainTx.toId != blockChainTx.toId) {
                log.warning(s"Local  is  ${resultBlockChainTx.toId}")
                log.warning(s"Remote is  ${blockChainTx.toId}")
              }
              sender() ! NetworkMessage(MessageKeys.AckConfirmTx, resultBlockChainTx.toId.toBytes)
          }
      }
  }


}
