package sss.openstar.chains

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import org.joda.time.DateTime
import sss.openstar.common.block._
import sss.openstar.actor.{OpenstarEventSubscribedActor, SystemPanic}
import sss.openstar.block._
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.network.MessageEventBus._
import sss.openstar.network._
import sss.openstar.util.ByteArrayComparisonOps
import sss.openstar.{MessageKeys, Send}
import sss.openstar.block.BlockChainLedger.NewBlockId
import sss.openstar.block.signature.BlockSignatures
import sss.openstar.block.signature.BlockSignatures.BlockSignature
import sss.openstar.chains.LeaderElectionActor.{LeaderLost, LocalLeader}
import sss.openstar.chains.SouthboundTxDistributorActor.{QuorumCandidates, SouthboundClose, SouthboundRejectedTx, SouthboundTx}
import sss.openstar.ledger.{LedgerException, LedgerItem, Ledgers}
import sss.db.Db
import sss.openstar.account.{NodeIdentity, PublicKeyAccount}

import scala.collection.SortedSet
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/*

https://stackoverflow.com/questions/1691179/is-tcp-guaranteed-to-arrive-in-order

 */
object TxDistributeeActor {

  case class CheckedProp(value:Props, name: String)

  def props(
            bc: BlockChain with BlockChainSignaturesAccessor,
            nodeIdentity: NodeIdentity
           )
           (implicit db: Db,
            chainId: GlobalChainIdMask,
            send: Send,
            messageEventBus: MessageEventBus,
            ledgers:Ledgers
           ): CheckedProp =
    CheckedProp(Props(classOf[TxDistributeeActor],
      bc,
      nodeIdentity,
      db,
      chainId,
      send,
      messageEventBus,
      ledgers),
      s"TxDistributeeActor_$chainId"
    )


  def apply(p:CheckedProp)(implicit actorSystem: ActorSystem): Unit = {
    actorSystem.actorOf(p.value.withDispatcher("blocking-dispatcher"), p.name)
  }
}

private class TxDistributeeActor(
                                 bc: BlockChain with BlockChainSignaturesAccessor,
                                 nodeIdentity: NodeIdentity
                    )(implicit db: Db,
                      chainId: GlobalChainIdMask,
                      send: Send,
                      messageEventBus: MessageEventBus,
                      ledgers:Ledgers
                                )
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with SystemPanic
    with OpenstarEventSubscribedActor {

  messageEventBus.subscribe(classOf[IsSynced])
  messageEventBus.subscribe(classOf[LocalLeader])
  messageEventBus.subscribe(classOf[LeaderLost])

  val distributeeNetMsgCodes = Seq(
    MessageKeys.CloseBlock,
    MessageKeys.BlockSig,
    MessageKeys.ConfirmTx,
    MessageKeys.CommittedTxId,
    MessageKeys.QuorumRejectedTx
  )

  log.info("TxDistributee actor has started...")

  var txCache: Map[BlockId, LedgerItem] = Map()


  override def receive: Receive = {

    case Synchronized(`chainId`, _, _, _) =>
      distributeeNetMsgCodes.subscribe

    case NotSynchronized(`chainId`) =>
      distributeeNetMsgCodes.unsubscribe

    case _ : LocalLeader =>
      distributeeNetMsgCodes.unsubscribe

    case _ : LeaderLost =>
      distributeeNetMsgCodes.subscribe

    case IncomingMessage(`chainId`, MessageKeys.BlockSig, leader, blkSig: BlockSignature) =>
      bc.blockHeaderOpt(blkSig.height) match {
        case None => log.error("Got a BlockSig for a non existent header .... {}", blkSig)
        case Some(header) =>
          val isOk = PublicKeyAccount(blkSig.publicKey).verify(blkSig.signature, header.hash)
          log.info("In BlockSig, verify sig from {} is {}", blkSig.nodeId, isOk)
          BlockSignatures.QuorumSigs(blkSig.height).write(blkSig)
      }


    case IncomingMessage(`chainId`, MessageKeys.ConfirmTx, leader, bTx: BlockChainTx) =>
      val blockLedger = BlockChainLedger(bTx.height)

      blockLedger.validate(bTx.blockTx) match {
        case Failure(e) =>
          val id = e match {
            case LedgerException(ledgerId, _) => ledgerId
            case _ => 0.toByte
          }
          log.info(s"Failed to validate tx! ${bTx} ${e.getMessage}")
          send(MessageKeys.NackConfirmTx, bTx.toId, leader)

        case Success(_) =>
          blockLedger.journal(bTx.blockTx)
          //log.info("Journal tx h: {} i: {}", height, bTx.index)
          txCache += BlockId(bTx.height, bTx.blockTx.index) -> bTx.blockTx.ledgerItem
          send(MessageKeys.AckConfirmTx, bTx.toId, leader)
      }


    case IncomingMessage(`chainId`, MessageKeys.QuorumRejectedTx, leader, bTx: BlockChainTxId) =>

      BlockChainLedger(bTx.height).rejected(bTx) match {
        case Failure(e) => systemPanic(e)

        case Success(_) =>
          log.info("Rejected tx h: {} i: {}", bTx.height, bTx.blockTxId.index)
          messageEventBus publish SouthboundRejectedTx(bTx)
      }

      txCache -= BlockId(bTx.height, bTx.blockTxId.index)

    case IncomingMessage(`chainId`, MessageKeys.CommittedTxId, leader,
                      bTx: BlockChainTxId) =>
      val bId = BlockId(bTx.height, bTx.blockTxId.index)
      val retrieved = txCache(bId)
      val blockTx = BlockTx(bId.txIndex, retrieved)

      BlockChainLedger(bId.blockHeight).commit(blockTx) match {
        case Failure(e) => systemPanic(e)

        case Success(events) =>
          log.info("Commit tx h: {} i: {}", bId.blockHeight, blockTx.index)
          messageEventBus publish SouthboundTx(BlockChainTx(bTx.height, blockTx))
          messageEventBus publish NewBlockId(chainId, bId)
            events foreach messageEventBus.publish
      }

      txCache -= bId


    case c @ IncomingMessage(`chainId`, MessageKeys.CloseBlock, nodeId, dc@DistributeClose(blockSigs, blockId)) =>

      bc.blockHeaderOpt(blockId.blockHeight) match {
        case None =>
          bc.closeBlock(blockId) match {

            case Failure(e) =>
              log.error("Couldn't close block {} {}", blockId, e)
              systemPanic(e)

            case Success(header) =>
              log.info("Close block h:{} numTxs: {}", header.height, header.numTxs)
              val sigsOk = blockSigs.forall {
                sig =>
                  val isOk = PublicKeyAccount(sig.publicKey).verify(sig.signature, header.hash)
                  log.info("Post close block, verify sig from {} is {}", sig.nodeId, isOk)
                  isOk
              }

              require(sigsOk, "Cannot continue block sig from quorum doesn't match that of local header hash")

              val blockSignatures = BlockSignatures.QuorumSigs(blockId.blockHeight)
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
              messageEventBus.publish(SouthboundClose(dc))
          }


        case Some(header) =>
          log.info("Already Closed!! block h:{} numTxs: {}", header.height, header.numTxs)

      }

  }
}
