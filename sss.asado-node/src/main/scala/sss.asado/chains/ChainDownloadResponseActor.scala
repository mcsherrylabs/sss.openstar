package sss.asado.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.MessageKeys._
import sss.asado.block.{BlockChain, BlockChainSignatures, BlockChainSignaturesAccessor, DistributeClose, GetTxPage, IsSynced, NotSynchronized, Synchronized}
import sss.asado.block.signature.BlockSignatures
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.SouthboundTxDistributorActor.SynchronizedConnection
import sss.asado.common.block._
import sss.asado.ledger._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.MessageEventBus
import sss.db.Db

import scala.language.postfixOps

/**
  * Sends pages of txs to a client trying to download the whole chain.
  *
  *
  * Created by alan on 3/24/16.
  */

object ChainDownloadResponseActor {

  def apply(
            maxSignatures: Int,
            bc: BlockChain with BlockChainSignaturesAccessor)
           (implicit actorSystem: ActorSystem,
            db:Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageEventBus: MessageEventBus)

  : Unit = {

    actorSystem.actorOf(
      Props(classOf[ChainDownloadResponseActor],
        maxSignatures,
        bc,
        db,
        chainId,
        send,
        messageEventBus)
        .withDispatcher("blocking-dispatcher")
    , s"ChainDownloadResponseActor_$chainId")
  }
}

private class ChainDownloadResponseActor(
                                         maxSignatures: Int,
                                         bc: BlockChain with BlockChainSignaturesAccessor)
                                        (implicit db: Db,
                                         chainId: GlobalChainIdMask,
                                         send: Send,
                                         messageEventBus: MessageEventBus) extends Actor with ActorLogging {

  messageEventBus.subscribe(classOf[IsSynced])
  messageEventBus.subscribe(MessageKeys.GetPageTx)


  private case class EndOfBlock(nodeId: UniqueNodeIdentifier, blockId: BlockId)
  private var canIssueSyncs = false

  log.info("ChainDownloadResponse actor has started...")

  override def receive: Receive = {
    case Synchronized(`chainId`, _, _, _) =>
      canIssueSyncs = true


    case NotSynchronized(`chainId`) =>
      canIssueSyncs = false

    case EndOfBlock(someNodeId, blockId) =>

      val closeBytes = DistributeClose(
        BlockSignatures.QuorumSigs(blockId.blockHeight)
          .signatures(maxSignatures), blockId)

      send(PagedCloseBlock, closeBytes, someNodeId)

    case IncomingMessage(`chainId`, GetPageTx, someClientNode, getTxPage @ GetTxPage(blockHeight, index, pageSize)) =>

      log.info(s"${someClientNode} asking me for $getTxPage")

      lazy val nextPage = bc.block(blockHeight).page(index, pageSize)
      val lastBlockHeader = bc.lastBlockHeader

      val localCurrentBlockHeight = lastBlockHeader.height + 1

      if (localCurrentBlockHeight >= blockHeight) {

        val pageIncremented = GetTxPage(blockHeight, index + nextPage.size, pageSize)

        for (i <- nextPage.indices) {
          nextPage(i) match {
            case Left(bytes) =>
              val le = bytes.toLedgerItem
              val bctx = BlockChainTx(blockHeight, BlockTx(index + i, le))
              send(MessageKeys.PagedTx, bctx, someClientNode)
            case Right(txId) =>
              send(MessageKeys.RejectedPagedTx, BlockChainTxId(blockHeight, BlockTxId(txId, index + 1)), someClientNode)
          }
        }

        if (nextPage.size == pageSize) {
          send(MessageKeys.EndPageTx, pageIncremented, someClientNode)
        } else if (localCurrentBlockHeight == blockHeight) {
          if(canIssueSyncs) {
            send(MessageKeys.Synced, pageIncremented, someClientNode)
            messageEventBus.publish(SynchronizedConnection(chainId, someClientNode))
          } else {
            send(MessageKeys.NotSynced, pageIncremented, someClientNode)
          }
        } else {
          self ! EndOfBlock(someClientNode, BlockId(blockHeight, index + nextPage.size - 1))
        }

      } else {
        log.info(s"${someClientNode} asking for block height of $getTxPage, but current block height is $localCurrentBlockHeight")
        send(MessageKeys.NotSynced, getTxPage, someClientNode)
      }


  }
}
