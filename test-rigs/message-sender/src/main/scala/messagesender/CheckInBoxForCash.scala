package messagesender

import akka.actor.{Actor, ActorLogging, ActorRef}
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.ledger._
import sss.asado.block._
import sss.asado.balanceledger._
import sss.asado.contract.SaleSecretDec
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.message.{AddressedMessage, CheckForMessages, ForceCheckForMessages, Message, MessageEcryption, MessageInBox, SavedAddressedMessage}
import sss.asado.message.MessageInBox.MessagePage
import sss.asado.network.NetworkController.SendToNodeId
import sss.asado.network.NetworkMessage
import sss.asado.state.HomeDomain
import sss.asado.wallet.Wallet
import sss.asado.wallet.WalletPersistence.Lodgement

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
/**
  * Created by alan on 7/13/16.
  */
object CheckInBoxForCash {
  case object CheckInBox
  case class BountyTracker(txIndex: TxIndex, txOutput: TxOutput, indexToMark: Long)
}


class CheckInBoxForCash(inBox: MessageInBox,
                        identityServiceQuery: IdentityServiceQuery,
                        nodeIdentity: NodeIdentity,
                        ncRef: ActorRef,
                        wallet: Wallet,
                        homeDomain: HomeDomain) extends Actor with ActorLogging {

  import CheckInBoxForCash._


  //private lazy val inBoxPager = inBox.inBoxPager(1)

  private var watchingBounties: Map[String, BountyTracker] = Map()

  def lastPage = {

    def getPrev(msgPage: MessagePage[Message]): MessagePage[Message] = {
      if (msgPage.hasPrev) getPrev(msgPage.prev)
      else msgPage
    }

    val res = getPrev(inBox.inBoxPager(1))
    val i = res.messages.headOption map (_.index)
    log.info(s"Reloaded last page got ${res.messages.size} messages, ${i}")
    res
  }

  def processMsg(msg: Message): Unit = {
    val le = msg.tx.toLedgerItem

    require(le.ledgerId == MessageKeys.BalanceLedger, s"Message download expecting a balance ledger entry ${MessageKeys.BalanceLedger}, but got ${le.ledgerId}")

    val sTx = le.txEntryBytes.toSignedTxEntry
    val tx = sTx.txEntryBytes.toTx
    val bount = tx.outs(1).amount
    val enc = MessageEcryption.encryptedMessage(msg.msg)
    Try(identityServiceQuery.account(msg.from)) match {
      case Failure(e) => log.info(s"Couldn't find ${msg.from}, try later...")
      case Success(sender) =>
        val msgText = enc.decrypt(nodeIdentity, sender.publicKey)
        val secret: Array[Byte] = msgText.secret
        claimBounty(msg.tx.toLedgerItem, secret, msg.index)
    }
  }

  def claimBounty(ledgerItem:LedgerItem, secret: Array[Byte], index: Long) = {
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
    watchingBounties += signedTx.txId.asHexStr -> BountyTracker(TxIndex(signedTx.txId, 0),out, index)
    ncRef ! SendToNodeId(NetworkMessage(MessageKeys.SignedTx, le.toBytes), homeDomain.nodeId)

  }


  private var failFactor = 1

  override def receive: Receive = {

    case CheckInBox =>
      log.info("Checking InBox")
      if(lastPage.messages.nonEmpty) {
        failFactor = 1
        val msg = lastPage.messages.head
        processMsg(msg)
        inBox.archive(msg.index)
        self ! CheckInBox

      } else {
        context.system.scheduler.scheduleOnce(10 * failFactor seconds, self, CheckInBox)
        if(failFactor < 10) failFactor += 1
      }

    case NetworkMessage(MessageKeys.AckConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      watchingBounties.get(bId.blockTxId.txId.asHexStr) match {
        case None => log.debug(s"Got an extra confirm for $bId")
        case Some(bountyTracker) =>
          log.info("Got a bounty, processing ")

          wallet.credit(Lodgement(bountyTracker.txIndex, bountyTracker.txOutput, bId.height))
          watchingBounties -= bId.blockTxId.txId.asHexStr
          log.info("Successfully processed bounty")
      }

    case NetworkMessage(MessageKeys.NackConfirmTx, bytes) =>
      val bId = bytes.toBlockChainIdTx
      watchingBounties -= bId.blockTxId.txId.asHexStr
      log.info("Failed to get a confirm for a bounty ")


    case NetworkMessage(MessageKeys.SignedTxAck, bytes) =>
      val bId = bytes.toBlockChainIdTx
      log.info("Got an ACK for a bounty ")


    case NetworkMessage(MessageKeys.SignedTxNack, bytes) =>
      val m = bytes.toTxMessage
      watchingBounties.get(m.txId.asHexStr) match {
        case None => log.info(s"WARN got NACK for a bounty-> ${m.msg}, and can't find it in the list.")
        case Some(bountyTracker) =>
          inBox.archive(bountyTracker.indexToMark)
          log.info(s"WARN got NACK for a bounty-> ${m.msg}, removed it lost ${bountyTracker.txOutput.amount}.")
      }
      watchingBounties -= m.txId.asHexStr




  }
}
