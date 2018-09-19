package sss.asado.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.{MessageKeys, UniqueNodeIdentifier}
import sss.asado.MessageKeys._
import sss.asado.block.{BlockChain, BlockChainSignatures, DistributeClose, GetTxPage, Synchronized}
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.common.block._
import sss.asado.ledger._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network.{MessageEventBus, NetSendTo, NetworkRef, SerializedMessage}
import sss.db.Db

import scala.language.postfixOps

/**
  * Sends pages of txs to a client trying to download the whole chain.
  *
  *
  * Created by alan on 3/24/16.
  */

object ChainDownloadResponseActor {

  def apply(send: NetSendTo,
            messageEventBus: MessageEventBus,
            maxSignatures: Int,
            bc: BlockChain with BlockChainSignatures)
           (implicit actorSystem: ActorSystem, db:Db, chainId: GlobalChainIdMask)

  : Unit = {

    actorSystem.actorOf(
      Props(classOf[ChainDownloadResponseActor],
        send,
        messageEventBus,
        maxSignatures,
        bc,
        db,
        chainId)
    )
  }
}

private class ChainDownloadResponseActor(send: NetSendTo,
                                         messageEventBus: MessageEventBus,
                                         maxSignatures: Int,
                                         bc: BlockChain with BlockChainSignatures)
                                        (implicit db: Db, chainId: GlobalChainIdMask) extends Actor with ActorLogging {

  messageEventBus.subscribe(classOf[Synchronized])
  messageEventBus.subscribe(MessageKeys.GetPageTx)
  messageEventBus.subscribe(MessageKeys.BlockNewSig)

  private case class GetTxPageWithRef(ref: ActorRef, servicePaused: Boolean, getTxPage: GetTxPage)
  private case class EndOfBlock(nodeId: UniqueNodeIdentifier, blockId: BlockId)
  private var canIssueSyncs = false

  log.info("ChainTxPageServerActor actor has started...")

  override def receive: Receive = {
    case Synchronized(`chainId`, _, _) =>
      canIssueSyncs = true

    case EndOfBlock(someNodeId, blockId) =>

      val closeBytes = DistributeClose(
        BlockSignatures(blockId.blockHeight)
          .signatures(maxSignatures), blockId)


      send(SerializedMessage(CloseBlock, closeBytes), someNodeId)

    case IncomingMessage(`chainId`, GetPageTx, someClientNode, getTxPage @ GetTxPage(blockHeight, index, pageSize)) =>

      log.info(s"${someClientNode} asking me for $getTxPage")

      lazy val nextPage = bc.block(blockHeight).page(index, pageSize)
      lazy val lastBlockHeader = bc.lastBlockHeader

      val maxHeight = lastBlockHeader.height + 1

      if (maxHeight >= blockHeight) {

        val pageIncremented = GetTxPage(blockHeight, index + nextPage.size, pageSize)

        for (i <- nextPage.indices) {
          val stxBytes: Array[Byte] = nextPage(i)
          val bctx = BlockChainTx(blockHeight, BlockTx(index + i, stxBytes.toLedgerItem))
          send(SerializedMessage(MessageKeys.PagedTx, bctx), someClientNode)
        }

        if (nextPage.size == pageSize) {
          send(SerializedMessage(MessageKeys.EndPageTx, pageIncremented), someClientNode)
        } else if (maxHeight == blockHeight) {
          if(canIssueSyncs) {
            send(SerializedMessage(MessageKeys.Synced, pageIncremented), someClientNode)
          } else {
            send(SerializedMessage(MessageKeys.NotSynced, pageIncremented), someClientNode)
          }
        } else {
          self ! EndOfBlock(someClientNode, BlockId(blockHeight, index + nextPage.size))
        }

      } else log.warning(s"${someClientNode} asking for block height of $getTxPage, current block height is $maxHeight")


    case IncomingMessage(`chainId`, BlockNewSig, someClient, BlockSignature(_, _, height, nodeId, publicKey, signature)) =>
      val newSig = bc.addSignature(height, signature, publicKey, nodeId)
      // TODO figure this out -> context.parent ! DistributeSig(newSig)

  }
}
