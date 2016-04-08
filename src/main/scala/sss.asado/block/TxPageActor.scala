package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import ledger._
import sss.asado.MessageKeys
import sss.asado.network.NetworkMessage
import sss.db.Db

/**
  * Created by alan on 3/24/16.
  */

class TxPageActor(bc: BlockChain)(implicit db: Db) extends Actor with ActorLogging {


  private case class EndOfPage(ref: ActorRef, bytes: Array[Byte])
  private case class EndOfBlock(ref: ActorRef, blockId: BlockId)

  private case class TxToReturn(ref: ActorRef, blockChainTx: BlockChainTx)

  override def receive: Receive = {

    case EndOfBlock(ref, blockId) =>
      ref ! NetworkMessage(MessageKeys.CloseBlock, blockId.toBytes)

    case EndOfPage(ref, getTxPageBytes) => ref ! NetworkMessage(MessageKeys.EndPageTx, getTxPageBytes)
    case synched @ ClientSynched(ref, currentBlockHeight, expectedNextMessage) =>
      context.parent  ! synched
      context.stop(self)

    case TxToReturn(ref, blockChainTx) =>
      ref ! NetworkMessage(MessageKeys.PagedTx, blockChainTx.toBytes)

    case netTxPage @ NetworkMessage(MessageKeys.GetPageTx, bytes) =>

      val getTxPage : GetTxPage = bytes.toGetTxPage
      val maxHeight = bc.lastBlock.height + 1
      if(maxHeight >= getTxPage.blockHeight) {
        val nextPage = Block(getTxPage.blockHeight).page(getTxPage.index, getTxPage.pageSize)
        for(i <- nextPage.indices) {
          val stxBytes: Array[Byte] = nextPage(i)
          val bctx = BlockChainTx(getTxPage.blockHeight, BlockTx(i, stxBytes.toSignedTx))
          self ! TxToReturn(sender(), bctx)
        }
        if (nextPage.size == getTxPage.pageSize) self ! EndOfPage(sender(), bytes)
        else if(maxHeight == getTxPage.blockHeight) self ! ClientSynched(sender(), maxHeight, getTxPage.index + nextPage.size)
        else self ! EndOfBlock(sender(), BlockId(getTxPage.blockHeight, (getTxPage.index * getTxPage.pageSize) + nextPage.size))
      } else log.warning(s"${sender} asking for block height of $getTxPage, current block height is $maxHeight")


  }
}
