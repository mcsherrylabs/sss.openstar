package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import ledger._
import sss.asado.MessageKeys
import sss.asado.network.NetworkMessage

/**
  * Sends pages of txs to a client trying to downlad the whole chain.
  * Then dies.
  *
  * Created by alan on 3/24/16.
  */
class TxPageActor(bc: BlockChain) extends Actor with ActorLogging {

  private case class EndOfPage(ref: ActorRef, bytes: Array[Byte])
  private case class EndOfBlock(ref: ActorRef, blockId: BlockId)
  private case class TxToReturn(ref: ActorRef, blockChainTx: BlockChainTx)

  override def receive: Receive = {

    case EndOfBlock(ref, blockId) =>
      ref ! NetworkMessage(MessageKeys.CloseBlock, blockId.toBytes)

    case EndOfPage(ref, getTxPageBytes) => ref ! NetworkMessage(MessageKeys.EndPageTx, getTxPageBytes)

    case TxToReturn(ref, blockChainTx) =>
      ref ! NetworkMessage(MessageKeys.PagedTx, blockChainTx.toBytes)

    case netTxPage @ NetworkMessage(MessageKeys.GetPageTx, bytes) =>

      val getTxPage : GetTxPage = bytes.toGetTxPage
      val sendr = sender()
      log.info(s"Another node asking me for $getTxPage")
      val maxHeight = bc.lastBlockHeader.height + 1
      if(maxHeight >= getTxPage.blockHeight) {
        val nextPage = bc.block(getTxPage.blockHeight).page(getTxPage.index, getTxPage.pageSize)
        val pageIncremented = GetTxPage(getTxPage.blockHeight, getTxPage.index + nextPage.size , getTxPage.pageSize)
        for(i <- nextPage.indices) {
          val stxBytes: Array[Byte] = nextPage(i)
          val bctx = BlockChainTx(getTxPage.blockHeight, BlockTx(getTxPage.index + i, stxBytes.toSignedTx))
          //log.info(s"Sending back page line -> $bctx")
          self ! TxToReturn(sendr, bctx)
        }
        if (nextPage.size == getTxPage.pageSize) self ! EndOfPage(sendr, pageIncremented.toBytes)
        else if(maxHeight == getTxPage.blockHeight) context.parent ! ClientSynched(sendr, pageIncremented)
        else self ! EndOfBlock(sender(), BlockId(getTxPage.blockHeight, getTxPage.index + nextPage.size))
      } else log.warning(s"${sender} asking for block height of $getTxPage, current block height is $maxHeight")


  }
}
