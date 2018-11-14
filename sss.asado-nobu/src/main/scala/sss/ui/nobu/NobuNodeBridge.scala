package sss.ui.nobu


import akka.actor.ActorRef
import sss.asado.account.PublicKeyAccount
import sss.asado.balanceledger.{TxIndex, TxOutput, _}
import sss.asado.ledger._
import sss.asado.message._
import sss.asado.wallet.Wallet
import sss.ui.reactor.Event
import com.vaadin.ui.Notification
import sss.asado.UniqueNodeIdentifier
/**
  * Created by alan on 6/15/16.
  */

object NobuNodeBridge {

  val NobuCategory = "nobu.ui"

  trait NobuEvent extends Event {
    override val category: String = NobuCategory
  }


  case class Fail(msg:String)
  case class Notify(msg:String, t: Notification.Type = Notification.Type.HUMANIZED_MESSAGE)
  //case class Connected(who:String) extends NobuEvent
  //case object LostConnection extends NobuEvent
  //case class WalletUpdate(sndr: ActorRef, txId: TxId, debits: Seq[TxInput], credits: Seq[TxOutput])
  case class ClaimBounty(index: Long, stx: SignedTxEntry, secret: Array[Byte]) extends NobuEvent

  case class MessageToSend(from: UniqueNodeIdentifier,
                           to : UniqueNodeIdentifier,
                           account: PublicKeyAccount,
                           text: String,
                           amount: Int,
                           sender: ActorRef) extends NobuEvent

  case class SentMessageToDelete(index:Long) extends NobuEvent
  case class MessageToDelete(index:Long) extends NobuEvent
  case class MessageToArchive(index:Long) extends NobuEvent
  case object ShowInBox extends NobuEvent
  //case class BountyTracker(sndr: ActorRef, wallet: Wallet, txIndex: TxIndex, txOutput: TxOutput, le:LedgerItem)

}
