package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block.{BlockChainTx, BlockTx, DistributeTx}
import com.google.common.primitives.Longs
import ledger._
import sss.asado.MessageKeys
import sss.asado.network.NetworkMessage

import scala.util.{Failure, Success}
/**
  * Created by alan on 3/18/16.
  */
class TxWriter(writeConfirmActor: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = working(None)

  override def postStop = log.warning(s"Tx Writer ($self) is down."); super.postStop

  private def writeStx(blockLedger: BlockChainLedger, signedTx: SignedTx): Unit = {
      blockLedger(signedTx) match {
        case Success(btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>
          val sendr = sender()
          sendr ! NetworkMessage(MessageKeys.SignedTxAck, Longs.toByteArray(height))
          writeConfirmActor ! DistributeTx(sendr, btx)
        case Failure(e) => {
          log.error(e, s"Failed to apply tx! ${e.getMessage}")
          sender() ! NetworkMessage(MessageKeys.SignedTxNack, e.getMessage.getBytes)
        }
      }
    }

  /**
    * On client Retry ...
    *
    * Normal scenario - client gets a block height response, this is the block the tx will be in.
    * At this point the tx is irreversible, however it is not confirmed.
    * The block cannot close until the confirms are in, so the whole network depends on getting those confirms in.
    *
    * The client will get the quorum of confirms and then it should move on. Not before. If it moves
    * on before the confirms it could send a tx which ends up at a peer before the tx on which it depends causing
    * the confirm to be rejected. A retry should work though.
    * This is a weakness.
    *
    * If the client receives a block number but no confirms it should include it's block number in the retry
    * If the client receives no block number, it can still ask for a retry and the lookup will be based on it's original
    * timestamp.
    *
    * The client really only needs the number of confirms to allow it to send a tx depending on the tx it just sent.
    * The client in this scenario should really use the Seq(stx) message.
    */


  def errorNoLedger: Unit = {
    val msg = "No ledger in play, cannnot handle signed tx message"
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.SignedTxNack, msg.getBytes)
  }

  def errorBadMessage: Unit = {
    val msg = "Cannot deserialise that message, wrong code for the bytes?"
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.MalformedMessage, msg.getBytes)
  }

  private def working(blockLedgerOpt: Option[BlockChainLedger]): Receive = {

    case BlockLedger(coordinator: ActorRef, blockLedger: BlockChainLedger) => {
      context.become(working(Some(blockLedger)))
      coordinator ! AcknowledgeNewLedger

    }

    case NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>

      blockLedgerOpt match {
        case Some(blockLedger) => bytes.toSeqSignedTx.ordered foreach { stx =>
          writeStx(blockLedger, stx)
        }
        case None => errorNoLedger
      }


    case NetworkMessage(MessageKeys.SignedTx, bytes) =>
      log.info(s"Got a signed tx ... ")

      bytes.toSignedTxTry match {
        case Success(stx) => blockLedgerOpt match {
          case Some(blockLedger) => writeStx(blockLedger, stx)
          case None => errorNoLedger
        }
        case Failure(e) => errorBadMessage
      }

  }

}
