package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout, Terminated}
import org.joda.time.DateTime
import sss.asado.common.block._
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.block._
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumMonitor.Quorum
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{MessageKeys, Send}
import sss.asado.account.NodeIdentity
import sss.asado.block.signature.BlockSignatures
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.db.Db

import scala.collection.SortedSet
import scala.language.postfixOps


object TxDistributeeActor {

  case class CheckedProp(value:Props) extends AnyVal

  def props(
            bc: BlockChain with BlockChainSignatures,
            nodeIdentity: NodeIdentity
           )
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageEventBus: MessageEventBus
           ): CheckedProp =
    CheckedProp(Props(classOf[TxDistributeeActor], messageEventBus, send, bc, nodeIdentity, db, chainId))


  def apply(p:CheckedProp)(implicit actorSystem: ActorSystem): Unit = {
    actorSystem.actorOf(p.value)
  }
}

private class TxDistributeeActor(
                                 messageEventBus: MessageEventBus,
                                 send: Send,
                                 bc: BlockChain with BlockChainSignatures,
                                 nodeIdentity: NodeIdentity
                    )(implicit db: Db, chainId: GlobalChainIdMask)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with AsadoEventSubscribedActor {

  messageEventBus.subscribe(MessageKeys.CloseBlock)
  messageEventBus.subscribe(MessageKeys.BlockSig)
  messageEventBus.subscribe(MessageKeys.Leader)
  messageEventBus.subscribe(MessageKeys.Synchronized)
  messageEventBus.subscribe(MessageKeys.ConfirmTx)
  messageEventBus.subscribe(MessageKeys.CommittedTx)

  log.info("TxDistributee actor has started...")

  var txCache: SortedSet[BlockChainTx] = SortedSet[BlockChainTx]()

  private case class WriteCursor(writeCandidates: SortedSet[BlockChainTx],
                                 height: Long, index: Long)

  private def writeWhileSequential(writeFrame: WriteCursor): WriteCursor = {

    val writeCandidate = writeFrame.writeCandidates.head

    if(writeCandidate.height  == writeFrame.height
      && writeCandidate.blockTx.index == writeFrame.index) {
      //journal to ledger, change the index.

      bc.block(writeCandidate.height)
        .journal(writeFrame.index,
          writeCandidate.blockTx.ledgerItem
        )

      writeWhileSequential(
        WriteCursor(txCache.tail, writeFrame.height, writeFrame.index + 1)
      )

    } else {
      writeFrame
    }
  }

  private def withCursor(frame:WriteCursor): Receive = {

    case IncomingMessage(`chainId`, MessageKeys.BlockSig, leader, blkSig: BlockSignature) =>
      //assert(leader == frame., "BlockSig did not come from leader")
      BlockSignatures(blkSig.height).write(blkSig)

    case IncomingMessage(`chainId`, MessageKeys.ConfirmTx, leader, bTx: BlockChainTx) =>
      bc.block(bTx.height).write(bTx.blockTx.ledgerItem)
      context become withCursor(frame.copy(writeCandidates = frame.writeCandidates + bTx))
      send(MessageKeys.AckConfirmTx, bTx.toId, leader)

    case IncomingMessage(`chainId`, MessageKeys.CommittedTx, leader,
                      bTx: BlockChainTxId) =>
      val newFrame = writeWhileSequential(frame)
      context become withCursor(newFrame)


    case IncomingMessage(`chainId`, MessageKeys.CloseBlock, nodeId, DistributeClose(blockSigs, blockId)) =>

      bc.closeBlock(bc.lastBlockHeader)

      val blockSignatures = BlockSignatures(blockId.blockHeight)

      blockSignatures.write(blockSigs)

      if (blockSignatures
        .indexOfBlockSignature(nodeIdentity.id)
        .isEmpty) {
        val blockHeader = bc.blockHeader(blockId.blockHeight)
        val sig = nodeIdentity.sign(blockHeader.hash)
        val newSig = BlockSignature(0,
          new DateTime(),
          blockHeader.height,
          nodeIdentity.id,
          nodeIdentity.publicKey,
          sig)

        send(MessageKeys.BlockNewSig,newSig, nodeId)
      }
      //val newSig = bc.addSignature(blkSig.height, blkSig.signature, blkSig.publicKey, blkSig.nodeId)

  }

  private def onSync: Receive = {

    case Synchronized(`chainId`, height, index) =>
      context become withCursor(WriteCursor(SortedSet[BlockChainTx](), height, index + 1))

  }

  override def receive: Receive = onSync
}
