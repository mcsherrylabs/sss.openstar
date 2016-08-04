package sss.asado.block

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, GetRoutees, Routees}
import block.DistributeClose
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger._
import sss.asado.block.signature.BlockSignatures
import sss.asado.ledger._
import sss.asado.network.MessageRouter.{RegisterRef, UnRegisterRef}
import sss.asado.network.NetworkMessage
import sss.asado.state.AsadoStateProtocol._
import sss.asado.util.SeqSerializer
import sss.asado.wallet.Wallet
import sss.asado.wallet.WalletPersistence.Lodgement
import sss.asado.{InitWithActorRefs, MessageKeys}
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.{Failure, Success, Try}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/15/16.
  */

trait BlockChainSettings {
  val inflationRatePerBlock: Int
  val maxTxPerBlock: Int
  val maxBlockOpenSecs: Int
  val maxSignatures: Int
  val spendDelayBlocks: Int
  val numTxWriters: Int
  val numBlocksCached: Int
}

case class BlockLedger(ref: ActorRef, blockLedger: Option[BlockChainLedger])
case object MaxBlockSizeOrOpenTimeReached
case class StartBlockChain(refToInform: ActorRef, something: Any)
case class BlockChainStarted(something: Any)
case class StopBlockChain(refToInform: ActorRef, something: Any)
case class CommandFailed(something: Any)
case class BlockChainStopped(something: Any)
case class TryCloseBlock(height: Long)
case class OkToCloseBlock(height: Long)
case object AcknowledgeNewLedger


/**
  * This is the actor that cause blocks to be formed.
  * When the time comes it sends all the tx writers a
  * new ledger to use and when they confirm that they are using it
  * it closes the current block.
  *
  * @param blockChainSettings
  * @param bc
  * @param writersRouterRef
  * @param blockChainSyncingActor
  * @param db
  */
class BlockChainActor(nodeIdentity: NodeIdentity,
                      blockChainSettings: BlockChainSettings,
                      bc: BlockChain with BlockChainTxConfirms with BlockChainSignatures,
                      stateMachine: ActorRef,
                      writersRouterRef: ActorRef,
                      blockChainSyncingActor: ActorRef,
                      wallet:Wallet
                      )(implicit db: Db, ledgers: Ledgers) extends Actor with ActorLogging {


  override def postStop = log.warning("BlockChain actor is down!"); super.postStop

  context watch writersRouterRef

  blockChainSyncingActor ! InitWithActorRefs(self)

  stateMachine ! RegisterStateEvents

  log.info("BlockChain actor has started...")

  private var cancellable: Option[Cancellable] = None

  private def startTimer(secs: Long) = {
    cancellable = Option(context.system.scheduler.scheduleOnce(
      FiniteDuration(secs, SECONDS ),
      self, MaxBlockSizeOrOpenTimeReached))
  }

  private def createLedger(lastClosedBlock: BlockHeader, blockHeightIncrement: Int = 1): BlockChainLedger = {
    val newBlockheight = lastClosedBlock.height + blockHeightIncrement
    BlockChainLedger(newBlockheight)
  }

  private def handleRouterDeath: Receive = {
    case Terminated(routerRef) =>
      log.error("The router has died.... and so must we.")
      context stop self

  }

  private var routees: IndexedSeq[_] = _

  private def awaitAcks(message: Any): Receive = {
    case AcknowledgeNewLedger =>
      routees = routees.filterNot {
        case r: ActorRefRoutee => r.ref == sender()
        case x => throw new Error(s"Got an unknown routee $x")
      }

      if(routees.isEmpty) {
        log.info(s"New state(ledger| paused) ack'd by all.")
        self ! message
      }

  }


  // there must be a last closed block or we cannot start up.
  override def receive: Receive = handleRouterDeath orElse initialize

  private def secondsToWait(lastClosedBlockTime: Date): Long = {
    val passedTimeSinceLastBlockMs = new Date().getTime - lastClosedBlockTime.getTime
    val nextBlockScheduledClose = (blockChainSettings.maxBlockOpenSecs * 1000) - passedTimeSinceLastBlockMs
    // blocks may not close less than 3 seconds apart to prevent block closes backing up.
    if(nextBlockScheduledClose > 3000) (nextBlockScheduledClose / 1000) else 3
  }



  private def startingBlockChain(startMsg : StartBlockChain, lastBlockHeader: BlockHeader): Receive = {

    case Routees(writers: IndexedSeq[_]) =>
      routees = writers
      context.become(awaitAcks(BlockChainStarted(startMsg)) orElse startingBlockChain(startMsg, lastBlockHeader))
      writersRouterRef ! Broadcast(BlockLedger(self, Option(createLedger(lastBlockHeader))))

    case bcs @ BlockChainStarted(StartBlockChain(ref, any)) =>
      context.become(waiting(lastBlockHeader))
      ref ! BlockChainStarted(any)
      startTimer(secondsToWait(lastBlockHeader.time))

    case StartBlockChain(ref, any) => ref ! CommandFailed(any)
    case StopBlockChain(ref, any) => ref ! CommandFailed(any)

  }

  private def stoppingBlockChain(stopMsg : StopBlockChain, lastBlockHeader: BlockHeader): Receive = {

    case Routees(writers: IndexedSeq[_]) =>
      routees = writers
      context.become(awaitAcks(BlockChainStopped(stopMsg)) orElse stoppingBlockChain(stopMsg, lastBlockHeader))
      writersRouterRef ! Broadcast(BlockLedger(self, None))

    case BlockChainStopped(StopBlockChain(ref, any)) =>
      ref ! BlockChainStopped(any)
      context.become(initialize)

    case StartBlockChain(ref, any) => ref ! CommandFailed(any)
    case StopBlockChain(ref, any) => ref ! CommandFailed(any)

  }

  private var weAreLeader = false

  private def initialize: Receive = {

    case NotOrderedEvent => weAreLeader = false

    case LocalLeaderEvent => weAreLeader = true

    case sbc @ StartBlockChain(ref, any) if(weAreLeader) =>
      context.become(handleRouterDeath orElse startingBlockChain(sbc, bc.lastBlockHeader))
      writersRouterRef ! GetRoutees

    case sbc @ StopBlockChain(ref, any) => ref ! BlockChainStopped(any)

  }

  private def waiting(lastClosedBlock: BlockHeader): Receive = {

    case TryCloseBlock(height) => blockChainSyncingActor ! EnsureConfirms(self, height, OkToCloseBlock(height))

    case OkToCloseBlock(height) =>
      // all are writing to new ledger, all are confirmed, close last block
      require(height == lastClosedBlock.height + 1,
        s"Trying to close a block $height that does not match the last confirmed block ${lastClosedBlock.height}")
      log.info(s"About to close block ${lastClosedBlock.height + 1}")
      Try(bc.closeBlock(lastClosedBlock)) match {
        case Success(newLastBlock) =>

          val sig = BlockSignatures(newLastBlock.height).add(
            nodeIdentity.sign(newLastBlock.hash),
              nodeIdentity.publicKey,
            nodeIdentity.id)

          log.info(s"Block ${newLastBlock.height} successfully saved with ${newLastBlock.numTxs} txs")
          blockChainSyncingActor ! DistributeClose(Seq(sig), BlockId(newLastBlock.height, newLastBlock.numTxs))


          // can safely send coinbase tx now....
          val txs = ledgers.coinbase(nodeIdentity, BlockId(newLastBlock.height, newLastBlock.numTxs))
          if(txs.nonEmpty) {
            val txsSerialized = SeqSerializer.toBytes(txs.map(_.toBytes))
            writersRouterRef ! NetworkMessage(MessageKeys.SeqSignedTx, txsSerialized)
            txs.foreach { le =>
              // if it's a signed tx entry, send it to the wallet, otherwise ignore.
              // currently (June) there are no other coinbase txs.
              if(le.ledgerId == MessageKeys.BalanceLedger) {
                Try(le.txEntryBytes.toSignedTxEntry).map { stx =>
                  val outs = stx.txEntryBytes.toTx.outs
                  outs.indices foreach { i =>
                    wallet.credit(Lodgement(TxIndex(stx.txId, i),outs(i), newLastBlock.height + 1))
                    log.info(s"Added ${outs(i).amount} to node ${nodeIdentity.id}'s wallet, balance now ${wallet.balance()} ")
                  }
                }
              }
            }
          }

          context.become(handleRouterDeath orElse waiting(newLastBlock))
          startTimer(secondsToWait(newLastBlock.time))
        case Failure(e) => log.error("FAILED TO CLOSE BLOCK! 'Game over man, game over...'", e)
      }

    case Routees(writers: IndexedSeq[_]) =>
      routees = writers
      context.become(handleRouterDeath orElse awaitAcks(TryCloseBlock(lastClosedBlock.height + 1)) orElse waiting(lastClosedBlock))
      writersRouterRef ! Broadcast(BlockLedger(self, Option(createLedger(lastClosedBlock, 2))))


    case MaxBlockSizeOrOpenTimeReached =>
      cancellable foreach { c =>
          c.cancel()
          log.info("Timeout cancelled or max size block reached, begin close Block process ...")
          writersRouterRef ! GetRoutees
          cancellable = None
      }

    case StartBlockChain(ref, any) => ref ! BlockChainStarted(any)

    case sbc @ StopBlockChain(ref, _) =>
      log.info("Attempting to stop blockchain")
      cancellable map (_.cancel())
      context.become(handleRouterDeath orElse stoppingBlockChain(sbc, lastClosedBlock))
      writersRouterRef ! GetRoutees
  }
}
