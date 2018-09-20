package sss.asado.chains

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import sss.asado.account.NodeIdentity
import sss.asado.actor.{AsadoEventPublishingActor, AsadoEventSubscribedActor, SystemPanic}
import sss.asado.block.signature.BlockSignatures.BlockSignature
import sss.asado.block.{BlockChain, BlockChainLedger, BlockChainSignatures, BlockClosedEvent, BlockHeader, DistributeClose}
import sss.asado.chains.BlockCloseDistributorActor.{CloseBlock, ProcessCoinBaseHook}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.QuorumMonitor.Quorum
import sss.asado.common.block._
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.network._
import sss.asado.util.ByteArrayComparisonOps
import sss.asado.{MessageKeys, Send}
import sss.db.Db

import scala.language.postfixOps
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


object BlockCloseDistributorActor {

  type ProcessCoinBaseHook = BlockHeader => Unit

  case class CloseBlock(height: Long)

  case class CheckedProp(value:Props)

  def props(ledger: BlockChainLedger,
            q: Quorum,
            messageEventBus: MessageEventBus,
            send: Send,
            processCoinBaseHook: ProcessCoinBaseHook
           )
           (implicit db: Db, chainId: GlobalChainIdMask): CheckedProp =
    CheckedProp(Props(classOf[TxDistributeeActor], ledger, q))


  def apply(p:CheckedProp)(implicit context: ActorContext): ActorRef = {
    context.actorOf(p.value)
  }
}

private class BlockCloseDistributorActor(ledger: BlockChainLedger,
                                         q: Quorum,
                                         bc: BlockChain with BlockChainSignatures,
                                         blockChainSettings: BlockChainSettings,
                                         nodeIdentity: NodeIdentity,
                                         processCoinBaseHook: ProcessCoinBaseHook,

                    )(implicit db: Db,
                      chainId: GlobalChainIdMask,
                      messageEventBus: MessageEventBus,
                      send: Send)
    extends Actor
    with ActorLogging
    with ByteArrayComparisonOps
    with AsadoEventPublishingActor
    with AsadoEventSubscribedActor
    with SystemPanic {

  messageEventBus.subscribe(classOf[Quorum])
  messageEventBus.subscribe(MessageKeys.BlockNewSig)


  log.info("BlockCloseDistributor actor has started...")

  private var currentQuorum = q

  private def waitForClose: Receive = {

    case IncomingMessage(`chainId`, MessageKeys.BlockNewSig, nodeId, bSig: BlockSignature) =>
      // do something.
      val sig = bc.addSignature(bSig.height, bSig.signature, bSig.publicKey, nodeId)

      send(MessageKeys.BlockSig, sig, currentQuorum.members)
      val currentNumSigsForBlock = bc.signatures(bSig.height, Int.MaxValue).size

      //TODO put a time limit on when this should end, can't accept sigs' indefinitely.
      if(blockChainSettings.maxSignatures <= currentNumSigsForBlock) {
        messageEventBus.unsubscribe(self)
        context stop self
      }

    case CloseBlock(height) =>

      Try(bc.closeBlock(bc.lastBlockHeader)) match {

        case Success(newLastBlock) =>

          publish(BlockClosedEvent(newLastBlock.height))

          val sig = bc.sign(nodeIdentity, newLastBlock)

          send(MessageKeys.CloseBlock,
            DistributeClose(Seq(sig), BlockId(newLastBlock.height,newLastBlock.numTxs)),
            currentQuorum.members)

          log.info(s"Block ${newLastBlock.height} successfully saved with ${newLastBlock.numTxs} txs")
          processCoinBaseHook(newLastBlock)

        case Failure(e) =>
          log.error("FAILED TO CLOSE BLOCK! {} {}", height, e)
          systemPanic()
      }
  }

  private def updateQuorum: Receive = {
    case quorum: Quorum =>
      currentQuorum = quorum
  }


  override def receive: Receive = updateQuorum orElse waitForClose

}
