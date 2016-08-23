package sss.ui.nobu



import akka.actor.ActorRef
import com.vaadin.ui.{Notification, UI}
import sss.asado.MessageKeys
import sss.asado.account.PublicKeyAccount
import sss.asado.balanceledger.{BalanceLedgerQuery, StandardTx, Tx, TxIndex, TxOutput}
import sss.asado.contract.{SaleOrReturnSecretEnc, SaleSecretDec, SingleIdentityEnc}
import sss.asado.crypto.SeedBytes
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.block._
import sss.asado.ledger._
import sss.asado.balanceledger._
import sss.asado.message._
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.network.NetworkController.SendToNodeId
import sss.asado.network.NetworkMessage
import sss.asado.state.HomeDomain
import sss.asado.wallet.WalletPersistence.Lodgement
import sss.ui.reactor.{Event, UIEventActor}

/**
  * Created by alan on 6/15/16.
  */

object NobuNodeBridge {

  val NobuCategory = "nobu.ui"

  trait NobuEvent extends Event {
    override val category: String = NobuCategory
  }

  case class WalletUpdate(txId: TxId, debits: Seq[TxInput], credits: Seq[TxOutput])
  case class ClaimBounty(ledgerItem: LedgerItem, secret: Array[Byte]) extends NobuEvent
  case class MessageToSend(to : Identity, account: PublicKeyAccount, text: String) extends NobuEvent
  case class SentMessageToDelete(index:Long) extends NobuEvent
  case class MessageToDelete(index:Long) extends NobuEvent
  case class MessageToArchive(index:Long) extends NobuEvent
  case object ShowInBox extends NobuEvent
  case class BountyTracker(txIndex: TxIndex, txOutput: TxOutput)

}


class NobuNodeBridge(nobuNode: NobuNode,
                     homeDomain: HomeDomain,
                     balanceLedgerQuery: BalanceLedgerQuery,
                     identityService: IdentityServiceQuery,
                     minNumBlocksInWhichToClaim: Int,
                     chargePerMessage: Int,
                     amountBuriedInMail: Int = 10) extends UIEventActor {

  import nobuNode._
  import NobuNodeBridge._
  private val userId: String = nodeIdentity.id
  private lazy val inBox = MessageInBox(userId)

  def createFundedTx: Tx = {
    wallet.createTx(chargePerMessage + amountBuriedInMail)

  }

  private def createPaymentOuts(to: Identity, secret: Array[Byte]): Seq[TxOutput] = {
    // Add 4 blocks to min to allow for the local ledger being some blocks behind the
    // up to date ledger.
    Seq(
      TxOutput(chargePerMessage, SingleIdentityEnc(homeDomain.nodeId.id)),
      TxOutput(amountBuriedInMail, SaleOrReturnSecretEnc(nodeIdentity.id, to, secret,
        nobuNode.currentBlockHeight + minNumBlocksInWhichToClaim + 4))
    )
  }

  private def signOutputs(tx:Tx, secret: Array[Byte]): SignedTxEntry = {
    wallet.sign(tx, secret)
  }


  var watchingBounties: Map[String, BountyTracker] = Map()
  var watchingMsgSpends: Map[String, WalletUpdate] = Map()

  override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      watchingBounties.get(bId.blockTxId.txId.toBase64Str) match {
        case None => log.debug(s"Got an extra confirm for $bId")
        case Some(bountyTracker) =>
          wallet.credit(Lodgement(bountyTracker.txIndex, bountyTracker.txOutput, bId.height))
          push(Notification.show(s"ca-ching! ${bountyTracker.txOutput.amount}"))
          watchingBounties -= bId.blockTxId.txId.toBase64Str
      }

    case NetworkMessage(MessageKeys.NackConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      //push(Notification.show(s"Got NAckConfirm $bId"))
      watchingBounties -= bId.blockTxId.txId.toBase64Str


    case NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      val bId = bytes.toBlockChainIdTx
      //push(Notification.show(s"Got ACK $bId"))

    case NetworkMessage(MessageKeys.TempNack, bytes) =>
      val m = bytes.toTxMessage
      //push(Notification.show(s"Got NACK ${m.msg}"))
      watchingBounties -= m.txId.toBase64Str

    case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      val m = bytes.toTxMessage
      //push(Notification.show(s"Got NACK ${m.msg}"))
      watchingBounties -= m.txId.toBase64Str

    case NetworkMessage(MessageKeys.MessageResponse, bytes) =>
      bytes.toMessageResponse match {
        case SuccessResponse(txId) =>
          watchingMsgSpends.get(txId.toBase64Str).map { walletUpdate =>
            wallet.update(walletUpdate.txId, walletUpdate.debits,walletUpdate.credits)
          }
          push(Notification.show(s"Message accepted!"))

        case FailureResponse(txId, info) => push(Notification.show(s"Message rejected $info"))
      }

    case ClaimBounty(ledgerItem, secret) =>
      val stx = ledgerItem.txEntryBytes.toSignedTxEntry
      val tx = stx.txEntryBytes.toTx
      val adjustedIndex = tx.outs.size - 1
      val ourBounty = tx.outs(adjustedIndex)
      val inIndex = TxIndex(tx.txId, adjustedIndex)
      val in = TxInput(inIndex, ourBounty.amount, SaleSecretDec)
      val out = TxOutput(ourBounty.amount, wallet.encumberToIdentity())
      val newTx = StandardTx(Seq(in), Seq(out))
      val sig = SaleSecretDec.createUnlockingSignature(newTx.txId, nodeIdentity.tag, nodeIdentity.sign, secret)
      val signedTx = SignedTxEntry(newTx.toBytes, Seq(sig))
      val le = LedgerItem(MessageKeys.BalanceLedger, signedTx.txId, signedTx.toBytes)
      watchingBounties += signedTx.txId.toBase64Str -> BountyTracker(TxIndex(signedTx.txId, 0),out)
      ncRef ! SendToNodeId(NetworkMessage(MessageKeys.SignedTx, le.toBytes), homeDomain.nodeId)


    case MessageToSend(to, account, text) => {

      val baseTx = createFundedTx
      val changeTxOut = baseTx.outs.take(1)
      val secret = SeedBytes(16)

      val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(nodeIdentity, account.publicKey, text, secret)
      val paymentOuts = createPaymentOuts(to, secret)

      val tx = wallet.appendOutputs(baseTx, paymentOuts : _*)

      val signedSTx = signOutputs(tx, secret)
      val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)

      val m : SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toBytes, le.toBytes)

      watchingMsgSpends += le.txIdHexStr -> WalletUpdate(tx.txId, tx.ins, changeTxOut)

      ncRef ! SendToNodeId(NetworkMessage(MessageKeys.MessageAddressed, m.addrMsg.toBytes), homeDomain.nodeId)
    }
    case MessageToDelete(index) => inBox.delete(index)
    case MessageToArchive(index) => inBox.archive(index)
    case SentMessageToDelete(index) => inBox.deleteSent(index)
  }
}
