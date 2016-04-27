package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import ledger._
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.block.signature.BlockSignatures
import sss.asado.network.NetworkMessage
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
/**
  * Sends pages of txs to a client trying to download the whole chain.
  *
  *
  * Created by alan on 3/24/16.
  */
class TxPageActor(maxSignatures: Int,
                  bc: BlockChain with BlockChainSignatures)(implicit db: Db) extends Actor with ActorLogging {

  private case class GetTxPageWithRef(ref: ActorRef, servicePaused: Boolean, getTxPage: GetTxPage)
  private case class EndOfPage(ref: ActorRef, bytes: Array[Byte])
  private case class EndOfBlock(ref: ActorRef, blockId: BlockId)
  private case class TxToReturn(ref: ActorRef, blockChainTx: BlockChainTx)

  override def receive: Receive = {

    case EndOfBlock(ref, blockId) =>
      val closeBytes = DistributeClose(BlockSignatures(blockId.blockHeight).signatures(maxSignatures), blockId).toBytes
      ref ! NetworkMessage(CloseBlock, closeBytes)

    case EndOfPage(ref, getTxPageBytes) => ref ! NetworkMessage(EndPageTx, getTxPageBytes)

    case TxToReturn(ref, blockChainTx) =>
      ref ! NetworkMessage(MessageKeys.PagedTx, blockChainTx.toBytes)

    case BlockChainStopped(getTxPageWithRef: GetTxPageWithRef) => self ! getTxPageWithRef.copy(servicePaused = true)

    case CommandFailed(getTxPageRef) =>
      context.system.scheduler.scheduleOnce(1 seconds, context.parent, StopBlockChain(self, getTxPageRef))

    case getTxPageRef @ GetTxPageWithRef(ref, servicePaused, getTxPage @ GetTxPage(blockHeight, index, pageSize)) =>

      log.info(s"Another node asking me for $getTxPage")
      if (!servicePaused && blockHeight == bc.lastBlockHeader.height) {
        context.parent ! StopBlockChain(self, getTxPageRef)
      } else {
        val maxHeight = bc.lastBlockHeader.height + 1
        if (maxHeight >= blockHeight) {
          val nextPage = bc.block(blockHeight).page(index, pageSize)
          val pageIncremented = GetTxPage(blockHeight, index + nextPage.size, pageSize)
          for (i <- nextPage.indices) {
            val stxBytes: Array[Byte] = nextPage(i)
            val bctx = BlockChainTx(blockHeight, BlockTx(index + i, stxBytes.toSignedTx))
            //log.info(s"Sending back page line -> $bctx")
            self ! TxToReturn(ref, bctx)
          }
          if (nextPage.size == pageSize) self ! EndOfPage(ref, pageIncremented.toBytes)
          else if (maxHeight == blockHeight) context.parent ! ClientSynched(ref, pageIncremented)
          else self ! EndOfBlock(ref, BlockId(blockHeight, index + nextPage.size))
        } else log.warning(s"${sender} asking for block height of $getTxPage, current block height is $maxHeight")
      }

    case netTxPage@NetworkMessage(GetPageTx, bytes) =>
      decode(GetPageTx, bytes.toGetTxPage) { getTxPage =>
        val sendr = sender()
        self ! GetTxPageWithRef(sendr, false, getTxPage)
      }

    case NetworkMessage(BlockNewSig, bytes) =>
      decode(BlockNewSig, bytes.toBlockSignature) { blkSig =>
          val newSig = bc.addSignature(blkSig.height, blkSig.signature, blkSig.publicKey, blkSig.nodeId)
          context.parent ! DistributeSig(newSig)
      }


  }
}
