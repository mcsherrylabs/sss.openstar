package sss.asado

import java.nio.charset.StandardCharsets

import sss.asado.common.block.BlockChainTxId
import sss.asado.eventbus.{MessageInfoComposite, MessageInfos, StringMessage}
import sss.asado.ledger._
import sss.asado.common.block._
import sss.asado.util.SeqSerializer

/**
  * Created by alan on 5/24/16.
  */
trait PublishedMessageKeys {

  val SignedTx: Byte = 100
  val SignedTxAck: Byte = 101
  val SignedTxNack: Byte = 102
  val SeqSignedTx: Byte = 103
  /**
    * TODO When asking a node to Confirm a tx
    * Sign the tx so that the network can reject malicious
    * attempts to bring down the network. ie confirm tx's will be
    * unquestionally journalled and if a partial block is committed when
    * it becomes leader, those bad txs will kill the network.
    */
  val ConfirmTx: Byte = 104
  val AckConfirmTx: Byte = 105
  val NackConfirmTx: Byte = 106
  val TempNack: Byte = 107

  val DistributeTx: Byte = 108
  val SignedTxConfirm: Byte = 109 //logically belongs with SignedTx, is a client confirm

  val MalformedMessage: Byte = 20
  val GenericErrorMessage: Byte = 21

  val BalanceLedger: Byte = 70
  val IdentityLedger: Byte = 71
  val QuorumLedger: Byte = 72

  protected val publishedMsgs: MessageInfos =
    MessageInfoComposite[LedgerItem](SignedTx, classOf[LedgerItem], _.toLedgerItem) +:
    MessageInfoComposite[BlockChainTxId](SignedTxAck, classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[TxMessage](SignedTxNack, classOf[TxMessage], _.toTxMessage) :+
    MessageInfoComposite[Seq[LedgerItem]](SeqSignedTx, classOf[Seq[LedgerItem]], SeqSerializer.fromBytes(_) map (_.toLedgerItem)) +:
    MessageInfoComposite[BlockChainTx](ConfirmTx, classOf[BlockChainTx], _.toBlockChainTx) +: //todo should be toBlockChainTxId?
    MessageInfoComposite[BlockChainTxId](SignedTxConfirm, classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[BlockChainTx](DistributeTx, classOf[BlockChainTx], _.toBlockChainTx) +:
    MessageInfoComposite[BlockChainTxId](AckConfirmTx, classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[BlockChainTxId](NackConfirmTx, classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[TxMessage](TempNack, classOf[TxMessage], _.toTxMessage) +:
    MessageInfoComposite[StringMessage](MalformedMessage, classOf[StringMessage], b => StringMessage(new String(b, StandardCharsets.UTF_8))) +:
    MessageInfoComposite[StringMessage](GenericErrorMessage, classOf[StringMessage], b => StringMessage(new String(b, StandardCharsets.UTF_8)))


}