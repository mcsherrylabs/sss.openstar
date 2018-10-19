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
  val SignedTxConfirm: Byte = 104 //logically belongs with SignedTx, is a client confirm

  val AckConfirmTx: Byte = 105
  val NackConfirmTx: Byte = 106
  val TempNack: Byte = 107
  val ConfirmTx: Byte = 108
  val CommittedTxId: Byte = 109
  val CommittedTx: Byte = 110
  val QuorumRejectedTx: Byte = 111


  val MalformedMessage: Byte = 20
  val GenericErrorMessage: Byte = 21

  val BalanceLedger: Byte = 70
  val IdentityLedger: Byte = 71
  val QuorumLedger: Byte = 72

  protected val publishedMsgs: MessageInfos =
    MessageInfoComposite[LedgerItem]    (SignedTx,            classOf[LedgerItem], _.toLedgerItem) +:
    MessageInfoComposite[BlockChainTxId](SignedTxAck,         classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[TxMessage]     (SignedTxNack,        classOf[TxMessage], _.toTxMessage) :+
    MessageInfoComposite[SeqLedgerItem] (SeqSignedTx,         classOf[SeqLedgerItem], _.toSeqLedgerItem) +:
    MessageInfoComposite[BlockChainTx]  (ConfirmTx,           classOf[BlockChainTx], _.toBlockChainTx) +:
    MessageInfoComposite[BlockChainTxId](SignedTxConfirm,     classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[BlockChainTxId](CommittedTxId,       classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[BlockChainTxId](QuorumRejectedTx,    classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[BlockChainTx]  (CommittedTx,         classOf[BlockChainTx], _.toBlockChainTx) +:
    MessageInfoComposite[BlockChainTxId](AckConfirmTx,        classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[BlockChainTxId](NackConfirmTx,       classOf[BlockChainTxId], _.toBlockChainTxId) +:
    MessageInfoComposite[TxMessage]     (TempNack,            classOf[TxMessage], _.toTxMessage) +:
    MessageInfoComposite[StringMessage] (MalformedMessage,    classOf[StringMessage], b => StringMessage(new String(b, StandardCharsets.UTF_8))) +:
    MessageInfoComposite[StringMessage] (GenericErrorMessage, classOf[StringMessage], b => StringMessage(new String(b, StandardCharsets.UTF_8)))


}