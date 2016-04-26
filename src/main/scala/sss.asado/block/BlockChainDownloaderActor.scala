package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import org.joda.time.DateTime
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.account.NodeIdentity
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.network.MessageRouter.Register
import sss.asado.network.NetworkController.SendToNetwork
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol.SyncWithLeader
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


class BlockChainDownloaderActor(nodeIdentity: NodeIdentity,
                                nc: ActorRef,
                                messageRouter: ActorRef,
                                bc: BlockChain with BlockChainSignatures)
                               (implicit db: Db) extends Actor with ActorLogging {


  private case class CommitBlock(serverRef: ActorRef,  blockId: BlockId, retryCount: Int = 0)

  messageRouter ! Register(PagedTx)
  messageRouter ! Register(EndPageTx)
  messageRouter ! Register(ConfirmTx)
  messageRouter ! Register(CloseBlock)
  messageRouter ! Register(BlockSig)
  messageRouter ! Register(Synced)


  private def makeNextLedger(lastHeight: Long) = BlockChainLedger(lastHeight + 1)

  override def postStop = log.warning("BlockChainDownloaderActor Monitor actor is down!"); super.postStop

  override def receive: Receive = syncLedgerWithLeader

  private var synced = false

  private def syncLedgerWithLeader: Receive = {

    case SyncWithLeader(leader) =>
        val getTxs = {
            val lb = bc.lastBlockHeader
            val blockStorage = Block(lb.height + 1)
            val indexOfLastRow = blockStorage.maxMonotonicCommittedIndex
            val startAtNextIndex = indexOfLastRow + 1
            GetTxPage(lb.height + 1, startAtNextIndex, 50)
        }

        nc ! SendToNetwork(NetworkMessage(MessageKeys.GetPageTx, getTxs.toBytes), (_ filter(_.nodeId.id == leader)) )


    case NetworkMessage(MessageKeys.PagedTx, bytes) =>
      decode(PagedTx, bytes.toBlockChainTx) { blockChainTx =>
        Try(BlockChainLedger(blockChainTx.height).journal(blockChainTx.blockTx)) match {
          case Failure(e) => log.error(e, s"Ledger cannot sync PagedTx, game over man, game over.")
          case Success(txDbId) => log.debug(s"CONFIRMED Up to $txDbId")
        }
      }


    case NetworkMessage(MessageKeys.EndPageTx, bytes) => sender() ! NetworkMessage(MessageKeys.GetPageTx, bytes)

    case CommitBlock(serverRef, blockId, reTryCount) if reTryCount > 5 => log.error(s"Ledger cannot sync retry, giving up.")

    case CommitBlock(serverRef, blockId, reTryCount) => {

      import sss.asado.util.ByteArrayVarcharOps._

      Try(BlockChainLedger(blockId.blockHeight).commit(blockId)) match {
        case Failure(e) =>
          log.error(e, s"Could not commit this block ${blockId}")
          context.system.scheduler.scheduleOnce(5000 millis, self, CommitBlock(serverRef, blockId, reTryCount + 1))
        case Success(_) =>
          Try(bc.closeBlock(bc.blockHeader(blockId.blockHeight - 1))) match {
            case Failure(e) => log.error(e, s"Ledger cannot sync close block , game over man, game over.")
            case Success(blockHeader) =>
              log.info(s"Synching - committed block height ${blockHeader.height}, num txs  ${blockHeader.numTxs}")
              if(BlockSignatures(blockHeader.height).indexOfBlockSignature(nodeIdentity.id).isEmpty) {
                val sig = nodeIdentity.sign(blockHeader.hash)

                log.info(s"SIGN ${blockHeader.height}")
                log.info(s"Key ${nodeIdentity.publicKey.toVarChar}")
                log.info(s"Sig ${sig.toVarChar}")
                log.info(s"Hash ${blockHeader.hash.toVarChar}")

                val newSig = BlockSignature(0, new DateTime(),
                  blockHeader.height,
                  nodeIdentity.id,
                  nodeIdentity.publicKey, sig)

                serverRef ! NetworkMessage(MessageKeys.BlockSig, newSig.toBytes)
              }
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
        val blockSignaturePersistance = BlockSignatures(distClose.blockId.blockHeight)
        distClose.blockSigs.foreach { sig =>
          blockSignaturePersistance.write(sig)
        }
        self ! CommitBlock(sender(), distClose.blockId)
      }

    case NetworkMessage(Synced, bytes) =>
      synced = true;
      val getTxPage = bytes.toGetTxPage
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
