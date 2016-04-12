package sss.asado.block

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, GetRoutees, Routees}
import block.{BlockId, DistributeClose, ReDistributeTx}
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
}

case class BlockLedger(ref: ActorRef, blockLedger: Option[BlockChainLedger])
case object MaxBlockOpenTimeElapsed
case class StartBlockChain(refToInform: ActorRef, something: Any)
case class BlockChainStarted(something: Any)
case class StopBlockChain(refToInform: ActorRef, something: Any)
case class CommandFailed(something: Any)
case class BlockChainStopped(something: Any)
case object TryCloseBlock
case object AcknowledgeNewLedger


/**
  * This is the actor that cause blocks to be formed.
  * When the time comes it sends all the tx writers a
  * new ledger to use and when they confirm that they are using it
  * they close the current block.
  *
  * @param blockChainSettings
  * @param bc
  * @param writersRouterRef
  * @param blockChainSyncingActor
  * @param db
  */
class BlockChainActor(blockChainSettings: BlockChainSettings,
                      bc: BlockChain with BlockChainTxConfirms,
                      writersRouterRef: ActorRef,
                      blockChainSyncingActor: ActorRef
                      )(implicit db: Db) extends Actor with ActorLogging {


  override def postStop = log.warning("BlockChain actor is down!"); super.postStop

  context watch writersRouterRef

  private var cancellable: Option[Cancellable] = None

  private def startTimer(secs: Long) = {
    cancellable = Option(context.system.scheduler.scheduleOnce(
      FiniteDuration(secs, SECONDS ),
      self, MaxBlockOpenTimeElapsed))
  }

  private def createLedger(lastClosedBlock: BlockHeader, blockHeightIncrement: Int = 1): BlockChainLedger = {
    val newBlockheight = lastClosedBlock.height + blockHeightIncrement
    BlockChainLedger(newBlockheight)
  }

  private def handleRouterDeath: Receive = {
    case Terminated(routerRef) => {
      log.error("The router has died.... and so must we.")
      context stop self
    }
  }

  private var routees: IndexedSeq[_] = _

  private def awaitAcks(message: Any): Receive = {
    case AcknowledgeNewLedger => {
      routees = routees.filterNot(_ match {
        case r: ActorRefRoutee => r.ref == sender()
        case x => throw new Error(s"Got an unknown routee $x")
      })

      if(routees.isEmpty) {
        log.info(s"New state(ledger| paused) ack'd by all.")
        // TODO Coin base transaction ??
        self ! message
      }

    }

  }


  // there must be a last closed block or we cannot start up.
  override def receive: Receive = handleRouterDeath orElse initialize

  private def secondsToWait(lastClosedBlockTime: Date): Long = {
    val passedTimeSinceLastBlockMs = (new Date().getTime) - lastClosedBlockTime.getTime
    val nextBlockScheduledClose = (blockChainSettings.maxBlockOpenSecs * 1000) - passedTimeSinceLastBlockMs
    if(nextBlockScheduledClose > 0) (nextBlockScheduledClose / 1000) else 1
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


  private def initialize: Receive = {

    case sbc @ StartBlockChain(ref, any) =>
      context.become(handleRouterDeath orElse startingBlockChain(sbc, bc.lastBlockHeader))
      writersRouterRef ! GetRoutees

    case sbc @ StopBlockChain(ref, any) => ref ! BlockChainStopped(any)


  }

  private def waiting(lastClosedBlock: BlockHeader): Receive = {

    case TryCloseBlock =>
      // all are writing to new ledger.
      // close last block
      val unconfirmed  = bc.getUnconfirmed(lastClosedBlock.height + 1, 1)
      if(unconfirmed.isEmpty) {

        log.info(s"About to close block ${lastClosedBlock.height + 1}")
        Try(bc.closeBlock(lastClosedBlock)) match {
          case Success(newLastBlock) =>
            log.info(s"Block ${newLastBlock.height} successfully saved with ${newLastBlock.numTxs} txs")
            blockChainSyncingActor ! DistributeClose(BlockId(newLastBlock.height, newLastBlock.numTxs))
            context.become(handleRouterDeath orElse waiting(newLastBlock))
            startTimer(secondsToWait(newLastBlock.time))

          case Failure(e) => log.error("FAILED TO CLOSE BLOCK! 'Game over man, game over...'", e)

        }
        //TODO Fix Redistribute
      } else {
        log.warning(s"there were ${unconfirmed.size} unconfirmed txs, retrying....")
        unconfirmed.foreach (unconfirmedTx => blockChainSyncingActor ! ReDistributeTx(unconfirmedTx))
        context.system.scheduler.scheduleOnce(
          FiniteDuration(5, SECONDS ),
          self, TryCloseBlock)
      }

    case Routees(writers: IndexedSeq[_]) =>
      routees = writers
      context.become(handleRouterDeath orElse awaitAcks(TryCloseBlock) orElse waiting(lastClosedBlock))
      writersRouterRef ! Broadcast(BlockLedger(self, Option(createLedger(lastClosedBlock, 2))))

    case MaxBlockOpenTimeElapsed => {
      log.info("Block open time elapsed, begin close process ...")
      writersRouterRef ! GetRoutees
    }

    case StartBlockChain(ref, any) => ref ! BlockChainStarted(any)
    case sbc @ StopBlockChain(ref, any) =>
      log.info("Attempting to stop blockchain")
      cancellable map (_.cancel())
      context.become(handleRouterDeath orElse stoppingBlockChain(sbc, lastClosedBlock))
      writersRouterRef ! GetRoutees
  }
}
