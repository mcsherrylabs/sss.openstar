package sss.asado.block

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, GetRoutees, Routees}
import sss.asado.ledger.{Ledger, UTXOLedger}
import sss.asado.storage.TxDBStorage
import sss.db.Db

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.util.{Failure, Success}

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/15/16.
  */

trait BlockChainSettings {
  val inflationRatePerBlock: Int
  val maxTxPerBlock: Int
  val maxBlockOpenSecs: Int
}

case class BlockLedger(ref: ActorRef, blockLedger: Ledger)
case object MaxBlockOpenTimeElapsed
case object AcknowledgeNewLedger


class BlockChainActor(blockChainSettings: BlockChainSettings,
                      bc: BlockChain,
                      utxoLedger: UTXOLedger,
                      writersRouterRef: ActorRef
                      )(implicit db: Db) extends Actor with ActorLogging {


  override def postStop = log.warning("BlockChain actor is down!"); super.postStop

  context watch writersRouterRef
  writersRouterRef ! GetRoutees


  private def startTimer(secs: Long) = {
    context.system.scheduler.scheduleOnce(
      FiniteDuration(secs, SECONDS ),
      self, MaxBlockOpenTimeElapsed)
  }

  private def createLedger(lastClosedBlock: BlockHeader, blockHeightIncrement: Int = 1): Ledger = {
    val newBlockheight = lastClosedBlock.height + blockHeightIncrement
    val txStorage = TxDBStorage(newBlockheight)
    new Ledger(newBlockheight, txStorage, utxoLedger)
  }

  private def handleRouterDeath: Receive = {
    case Terminated(routerRef) => {
      log.error("The router has died.... and so must we.")
      context stop self
    }
  }

  private def awaitAcks(lastClosedBlock: BlockHeader, routees: IndexedSeq[_], action: (BlockHeader) => Unit): Receive = {
    case AcknowledgeNewLedger => {
      val unconfirmedRoutees = routees.filterNot(_ match {
        case r: ActorRefRoutee => r.ref == sender()
        case x => throw new Error(s"Got an unknown routee $x")
      })

      if(unconfirmedRoutees.size == 0) {
        log.info(s"New ledger ack'd by all.")
        action(lastClosedBlock)
      }
      else context.become(handleRouterDeath orElse awaitAcks(lastClosedBlock, unconfirmedRoutees, action))
    }
    case MaxBlockOpenTimeElapsed => log.error("Max block open time has elapsed without tx writer acknowledgements...")
  }


  // there must be a last closed block or we cannot start up.
  override def receive: Receive = handleRouterDeath orElse initializeRoutees(bc.lastBlock.get)

  private def secondsToWait(lastClosedBlockTime: Date): Long = {
    val passedTimeSinceLastBlockMs = (new Date().getTime) - lastClosedBlockTime.getTime
    val nextBlockScheduledClose = (blockChainSettings.maxBlockOpenSecs * 1000) - passedTimeSinceLastBlockMs
    if(nextBlockScheduledClose > 0) (nextBlockScheduledClose / 1000) else 1
  }


  private def closeBlock(lastClosedBlock: BlockHeader): Unit = {
    // all are writing to new ledger.
    // close last block
    log.info(s"About to close block ${lastClosedBlock.height + 1}")
    bc.closeBlock(lastClosedBlock) match {
      case Success(newLastBlock) => {
        log.info(s"Block ${newLastBlock.height} successfully saved.")
        context.become(handleRouterDeath orElse waiting(newLastBlock))
        startTimer(secondsToWait(newLastBlock.time))
      }
      case Failure(e) => {
        log.error("FAILED TO CLOSE BLOCK! 'Game over man, game over...'", e)
      }
    }
  }

  private def startWaiting(lastClosedBlock: BlockHeader): Unit = {
    // all are writing to correct ledger.
    context.become(waiting(lastClosedBlock))
    startTimer(secondsToWait(lastClosedBlock.time))
  }

  private def initializeRoutees(lastClosedBlock: BlockHeader): Receive = {

    case Routees(routees: IndexedSeq[_]) =>
      log.info(s"Last known block is ${lastClosedBlock.height}, creating new ledgers...")
      context.become(awaitAcks(lastClosedBlock, routees, startWaiting))
      writersRouterRef ! Broadcast(BlockLedger(self, createLedger(lastClosedBlock)))

  }

  private def waiting(lastClosedBlock: BlockHeader): Receive = {

    case Routees(writers: IndexedSeq[_]) =>
      context.become(awaitAcks(lastClosedBlock, writers, closeBlock))
      writersRouterRef ! Broadcast(BlockLedger(self, createLedger(lastClosedBlock, 2)))

    case MaxBlockOpenTimeElapsed => {
      log.info("Block open time elapsed, begin close process ...")
      writersRouterRef ! GetRoutees
    }
  }
}
