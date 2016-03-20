package sss.asado.block

import akka.actor.{Actor, ActorRef}
import akka.routing.{GetRoutees, Routees}
import sss.ancillary.Logging
import sss.asado.storage.TxDBStorage

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/15/16.
  */


trait BlockChainSettings {
  val inflationRatePerBlock: Int
  val maxTxPerBlock: Int
  val maxBlockOpenMs: Int
}

class BlockChainActor(blockChainSettings: BlockChainSettings,
                      bc: BlockChain,
                      writersRouterRef: ActorRef
                      ) extends Actor with Logging {


  val lastClosedBlock = bc.lastBlock.get

  writersRouterRef ! GetRoutees
  

  /*match {
        case Failure(e) => {
          log.error(s"This block ($height)could not be saved, tx rolled back.", e)
          throw e
        }
        case Success(newBlock) => {
          log.info(s"Block $height successfully saved.")
          newBlock
        }*/

  override def receive: Receive = enumerateWriters(new TxDBStorage(bc.blockTableNamePrefix + (lastClosedBlock.height + 1)))

  private def enumerateWriters(txStorage: TxDBStorage): Receive = {
    case Routees(writers) => writers foreach (r => r.send(txStorage, self))
  }
}
