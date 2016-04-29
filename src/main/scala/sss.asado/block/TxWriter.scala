package sss.asado.block

import akka.actor.{Actor, ActorLogging, ActorRef}
import block.{BlockChainTx, BlockTx, DistributeTx, TxMessage}
import ledger.{SignedTx, _}
import sss.asado.MessageKeys
import sss.asado.network.NetworkMessage
import sss.asado.util.ByteArrayVarcharOps._

import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 3/18/16.
  */
class TxWriter(writeConfirmActor: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = working(None)

  override def postStop = log.warning(s"Tx Writer ($self) is down."); super.postStop

  private def writeStx(blockLedger: BlockChainLedger, signedTx: SignedTx): Unit = {
      val sendr = sender()
      Try(blockLedger(signedTx)) match {
        case Success(btx @ BlockChainTx(height, BlockTx(index, signedTx))) =>
          sendr ! NetworkMessage(MessageKeys.SignedTxAck, btx.toId.toBytes)
          writeConfirmActor ! DistributeTx(sendr, btx)
        case Failure(e) => {
          log.info(s"Failed to ledger tx! ${signedTx.txId.toVarChar} ${e.getMessage}")
          sendr ! NetworkMessage(MessageKeys.SignedTxNack, TxMessage(signedTx.txId, e.getMessage).toBytes)
        }
      }
    }

  /**
    * On client Retry ...
    *
    * Normal scenario - client gets a block tx id it will be in.
    * At this point the tx is irreversible, however it is not confirmed.
    * The block cannot close until the confirms are in, so the whole network depends on getting those confirms in.
    *
    * The client will get the quorum of confirms and then it should move on. Not before. If it moves
    * on before the confirms it could send a tx which ends up at a peer before the tx on which it depends causing
    * the confirm to be rejected. A retry should work though.
    * This is a weakness.
    *
    * If the client receives a block tx id but no confirms it should include it's block number in the retry
    * If the client receives no block number, it can still ask for a retry and the lookup will be based on it's original
    * timestamp.
    *
    * The client really only needs the number of confirms to allow it to send a tx depending on the tx it just sent.
    * The client in this scenario should really use the Seq(stx) message.
    */


  def errorNoLedger(txId: TxId): Unit = {
    val msg = "No ledger open, retry later."
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.SignedTxNack, TxMessage(txId, msg).toBytes)
  }

  def errorBadMessage: Unit = {
    val msg = "Cannot deserialise that message, wrong code for the bytes?"
    log.error(msg)
    sender() ! NetworkMessage(MessageKeys.MalformedMessage, msg.getBytes)
  }

  private def working(blockLedgerOpt: Option[BlockChainLedger]): Receive = {

    case BlockLedger(blockChainActor: ActorRef, blockLedger: Option[BlockChainLedger]) => {
      context.become(working(blockLedger))
      blockChainActor ! AcknowledgeNewLedger

    }

    case NetworkMessage(MessageKeys.SeqSignedTx, bytes) =>

      blockLedgerOpt match {
        case Some(blockLedger) => bytes.toSeqSignedTx.ordered foreach { stx =>
          writeStx(blockLedger, stx)
        }
        case None => bytes.toSeqSignedTx.ordered.foreach(stx => errorNoLedger(stx.txId))
      }


    case NetworkMessage(MessageKeys.SignedTx, bytes) =>

      Try(bytes.toSignedTx) match {
        case Success(stx) => blockLedgerOpt match {
          case Some(blockLedger) => writeStx(blockLedger, stx)
          case None => errorNoLedger(stx.txId)
        }
        case Failure(e) => errorBadMessage
      }

  }

}
