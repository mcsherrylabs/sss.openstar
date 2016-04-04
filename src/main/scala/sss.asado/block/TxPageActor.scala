package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block._
import sss.asado.MessageKeys
import sss.asado.network.NetworkMessage
import sss.asado.storage.TxDBStorage
import sss.db.Db

/**
  * Created by alan on 3/24/16.
  */

class TxPageActor(bc: BlockChain)(implicit db: Db) extends Actor with ActorLogging {


  private case class EndOfPage(ref: ActorRef)
  private case class EndOfBlock(ref: ActorRef)

  private case class TxToReturn(ref: ActorRef, stxBytes: Array[Byte])

  override def receive: Receive = {

    case EndOfBlock(ref) => ref ! NetworkMessage(MessageKeys.CloseBlock, Array())
    case EndOfPage(ref) => ref ! NetworkMessage(MessageKeys.EndPageTx, Array())
    case synched @ ClientSynched(ref) =>
      context.parent  ! synched
      context.stop(self)

    case TxToReturn(ref, stxBytes) => ref ! NetworkMessage(MessageKeys.PagedTx, stxBytes)

    case netTxPage @ NetworkMessage(MessageKeys.GetTxPage, bytes) =>
      val getTxPage : GetTxPage = bytes.toGetTxPage
      val maxHeight = bc.lastBlock.height + 1
      if(maxHeight >= getTxPage.blockHeight) {
        val nextPage = TxDBStorage(getTxPage.blockHeight).page(getTxPage.index, getTxPage.pageSize)
        nextPage.foreach(self ! TxToReturn(sender(), _))
        if (nextPage.size == getTxPage.pageSize) self ! EndOfPage(sender())
        else if(maxHeight == getTxPage.blockHeight) self ! ClientSynched(sender())
        else self ! EndOfBlock(sender())
      } else log.warning(s"${sender} asking for block height of $getTxPage, current block height is $maxHeight")


  }
}
