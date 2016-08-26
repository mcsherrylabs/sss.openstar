package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import sss.asado.MessageKeys
import sss.asado.MessageKeys._
import sss.asado.block.signature.BlockSignatures
import sss.asado.ledger._
import sss.asado.network.NetworkMessage
import sss.db.Db

import scala.language.postfixOps

/**
  * Embarrassingly, I failed to extract the common code from TxPageActor
  * This does almost the same thing, but is used only for clients and not
  * for nodes forming part of the core peer set.
  *
  * Created by alan on 3/24/16.
  */
class SimpleTxPageActor(maxSignatures: Int,
                        bc: BlockChain)(implicit db: Db) extends Actor with ActorLogging {

  private case class GetTxPageRef(ref: ActorRef, getTxPage: GetTxPage)
  private case class EndOfPage(ref: ActorRef, bytes: Array[Byte])
  private case class EndOfBlock(ref: ActorRef, blockId: BlockId)
  private case class TxToReturn(ref: ActorRef, blockChainTx: BlockChainTx)

  log.info("Simple TxPage actor has started...")



  override def receive: Receive = {

    case EndOfBlock(ref, blockId) =>
      val closeBytes = DistributeClose(BlockSignatures(blockId.blockHeight).signatures(maxSignatures), blockId).toBytes
      ref ! NetworkMessage(SimpleCloseBlock, closeBytes)

    case EndOfPage(ref, getTxPageBytes) => ref ! NetworkMessage(SimpleEndPageTx, getTxPageBytes)

    case TxToReturn(ref, blockChainTx) =>
      ref ! NetworkMessage(MessageKeys.SimplePagedTx, blockChainTx.toBytes)

    case getTxPageRef @ GetTxPageRef(ref, getTxPage @ GetTxPage(blockHeight, index, pageSize)) =>

      log.info(s"${ref} asking me for $getTxPage")
      lazy val nextPage = bc.block(blockHeight).page(index, pageSize)
      lazy val lastBlockHeader = bc.lastBlockHeader
      val maxHeight = lastBlockHeader.height + 1
      if (maxHeight >= blockHeight) {
        val pageIncremented = GetTxPage(blockHeight, index + nextPage.size, pageSize)
        for (i <- nextPage.indices) {
           val stxBytes: Array[Byte] = nextPage(i)
           val bctx = BlockChainTx(blockHeight, BlockTx(index + i, stxBytes.toLedgerItem))
           self ! TxToReturn(ref, bctx)
        }
        if (nextPage.size == pageSize) self ! EndOfPage(ref, pageIncremented.toBytes)
        else if (maxHeight == blockHeight) ref ! NetworkMessage(SimpleGetPageTxEnd, pageIncremented.toBytes)
        else self ! EndOfBlock(ref, BlockId(blockHeight, index + nextPage.size))
      } else log.warning(s"${sender} asking for block height of $getTxPage, current block height is $maxHeight")


    case netTxPage@NetworkMessage(SimpleGetPageTx, bytes) =>
      decode(SimpleGetPageTx, bytes.toGetTxPage) { getTxPage =>
        self ! GetTxPageRef(sender(), getTxPage)
      }

  }
}
