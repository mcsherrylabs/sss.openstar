package sss.ui.nobu



import akka.actor.ActorRef
import com.vaadin.ui.{Notification, UI}
import sss.asado.MessageKeys
import sss.asado.account.PublicKeyAccount
import sss.asado.balanceledger.{BalanceLedgerQuery, StandardTx, Tx, TxIndex, TxOutput, _}
import sss.asado.block._
import sss.asado.contract.{SaleOrReturnSecretEnc, SaleSecretDec, SingleIdentityEnc}
import sss.asado.crypto.SeedBytes
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger._
import sss.asado.message._
import sss.asado.network.NetworkController.SendToNodeId
import sss.asado.network.NetworkMessage
import sss.asado.state.HomeDomain
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.wallet.Wallet
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

  case class Connected(who:String) extends NobuEvent
  case object LostConnection extends NobuEvent
  case class WalletUpdate(sndr: ActorRef, txId: TxId, debits: Seq[TxInput], credits: Seq[TxOutput])
  case class ClaimBounty(stx: SignedTxEntry, secret: Array[Byte]) extends NobuEvent
  case class MessageToSend(to : Identity, account: PublicKeyAccount, text: String, amount: Int) extends NobuEvent
  case class SentMessageToDelete(index:Long) extends NobuEvent
  case class MessageToDelete(index:Long) extends NobuEvent
  case class MessageToArchive(index:Long) extends NobuEvent
  case object ShowInBox extends NobuEvent
  case class BountyTracker(sndr: ActorRef, wallet: Wallet, txIndex: TxIndex, txOutput: TxOutput, le:LedgerItem)

}
