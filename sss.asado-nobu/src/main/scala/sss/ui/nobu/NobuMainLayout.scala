package sss.ui.nobu

import akka.actor.{ActorRef, Props}
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui.{Notification, UI}
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{StandardTx, TxIndex, TxInput, TxOutput}
import sss.asado.contract.{SaleOrReturnSecretEnc, SaleSecretDec, SingleIdentityEnc}
import sss.asado.crypto.SeedBytes
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.message.MessageInBox.MessagePage
import sss.asado.message.{ForceCheckForMessages, Message, MessageEcryption, MessageInBox, SavedAddressedMessage}
import sss.asado.network.NetworkController.SendToNodeId
import sss.asado.network.NetworkMessage
import sss.asado.nodebuilder.ClientNode
import sss.asado.wallet.Wallet
import sss.asado.ledger._
import sss.asado.message._
import sss.asado.balanceledger._
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.db.Db
import sss.ui.design.NobuMainDesign
import sss.ui.nobu.NobuNodeBridge._
import sss.ui.reactor.{ComponentEvent, Register, UIEventActor, UIReactor}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */
case class ShowWrite(to: String = "", text: String = "")
case class Show(s: String)
case class Bag(userWallet: Wallet, sTx: SignedTxEntry, msg: SavedAddressedMessage, walletUpdate: WalletUpdate, from: String)

class NobuMainLayout(uiReactor: UIReactor,
                     userWallet: Wallet,
                     userId: NodeIdentity,
                     nobuNode: ClientNode,
                     clientEventActor: ActorRef) extends NobuMainDesign with View with Logging {

  private implicit val db: Db = nobuNode.db
  private implicit val identityQuery: IdentityServiceQuery = nobuNode.identityService
  private implicit val nodeIdentity = userId


  private lazy val minNumBlocksInWhichToClaim = nobuNode.conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  private lazy val chargePerMessage = nobuNode.conf.getInt("messagebox.chargePerMessage")
  private lazy val amountBuriedInMail = nobuNode.conf.getInt("messagebox.amountBuriedInMail")

  statusButton.setVisible(false)
  settingsButton.setVisible(false)

  val name = "main"

  val inBoxBtn = inboxButton
  val writeBtn = writeButton
  val archiveBtn = archiveButton
  val sentBtn = sentButton
  val logoutBtn = logoutButton
  val prevBtn = prevButton
  val nextBtn = nextButton
  val balBtnLbl = balanceValuBtnLbl

  writeBtn.addClickListener(uiReactor)
  inBoxBtn.addClickListener(uiReactor)
  archiveBtn.addClickListener(uiReactor)
  sentBtn.addClickListener(uiReactor)
  logoutBtn.addClickListener(uiReactor)
  nextBtn.addClickListener(uiReactor)
  prevBtn.addClickListener(uiReactor)
  balBtnLbl.addClickListener(uiReactor)



  val mainNobuRef = uiReactor.actorOf(Props(NobuMainActor),
    logoutButton, inBoxBtn, writeBtn, archiveBtn, sentBtn, nextBtn, prevBtn, balBtnLbl)

  mainNobuRef ! Register(NobuNodeBridge.NobuCategory)

  private lazy val nId = userId.id
  private lazy val inBox = MessageInBox(nId)

  balanceCptnBtn.setCaption(nId)

  mainNobuRef ! ShowInBox

  balBtnLbl.click()


  private def updatePagingAreas(pager: MessagePage[_], isForDeletion: Boolean = false): Unit = {
    prevBtn.setEnabled(pager.hasNext)
    nextBtn.setEnabled(pager.hasPrev)
    itemPanelVerticalLayout.removeAllComponents

    pager.messages.reverse foreach {

      case msg : Message if(isForDeletion) =>
        itemPanelVerticalLayout.addComponent(new DeleteMessageComponent(itemPanelVerticalLayout,
          mainNobuRef, msg))

      case msg: Message =>
        Try {
          val newMsg = new NewMessageComponent(itemPanelVerticalLayout,
            mainNobuRef, msg)

        itemPanelVerticalLayout.addComponent(newMsg)
        } match {
          case Failure(e) => log.warn(e.toString)
          case Success(_) =>
      }

      case msg: SavedAddressedMessage =>
        itemPanelVerticalLayout.addComponent(new SentMessageComponent(itemPanelVerticalLayout,
          mainNobuRef, msg))
      }

  }

  setSizeFull

  object NobuMainActor extends UIEventActor {


    def createFundedTx(amount: Int): Tx = {
      userWallet.createTx(amount)

    }

    private def createPaymentOuts(to: Identity, secret: Array[Byte], amount: Int): Seq[TxOutput] = {
      // Add 4 blocks to min to allow for the local ledger being some blocks behind the
      // up to date ledger.
      Seq(
        TxOutput(chargePerMessage, SingleIdentityEnc(nobuNode.homeDomain.nodeId.id)),
        TxOutput(amount, SaleOrReturnSecretEnc(nodeIdentity.id, to, secret,
          nobuNode.currentBlockHeight + minNumBlocksInWhichToClaim + 4))
      )
    }

    private def signOutputs(tx:Tx, secret: Array[Byte]): SignedTxEntry = {
      userWallet.sign(tx, secret)
    }

    def initInBoxPager = inBox.inBoxPager(4)
    def initSentPager = inBox.sentPager(4)
    def initArchivedPager = inBox.archivedPager(4)

    var pager: MessagePage[_] = initInBoxPager

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

      case Show(s) => push(Notification.show(s))

      case ShowWrite(to, text) => push {
        itemPanelVerticalLayout.removeAllComponents()
        itemPanelVerticalLayout.addComponent(new WriteLayout(mainNobuRef, to, text))
      }

      case ShowInBox =>
        pager = initInBoxPager
        Try(push(updatePagingAreas(pager))) match {
          case Failure(e) => push(Notification.show(e.getMessage))
          case _ =>
        }

      case ComponentEvent(`prevBtn`, _) =>
        if(pager.hasNext) {
          pager = pager.next
          Try(push(updatePagingAreas(pager)))  match {
            case Failure(e) => push(Notification.show(e.getMessage))
            case _ =>
          }
        }

      case ComponentEvent(`nextBtn`, _) =>
        if(pager.hasPrev) {
          pager = pager.prev
          Try(push(updatePagingAreas(pager)))  match {
            case Failure(e) => push(Notification.show(e.getMessage))
            case _ =>
          }

        }

      case ComponentEvent(`writeBtn`, _) =>
        self ! ShowWrite()

      case ComponentEvent(`sentBtn`, _) =>
        pager = initSentPager
        Try(push(updatePagingAreas(pager)))  match {
          case Failure(e) => push(Notification.show(e.getMessage))
          case _ =>
        }


      case ComponentEvent(`inBoxBtn`, _) =>
        self ! ShowInBox

      case ComponentEvent(`archiveBtn`, _) =>
        pager = initArchivedPager
        Try(push(updatePagingAreas(pager, isForDeletion = true)))  match {
          case Failure(e) => push(Notification.show(e.getMessage))
          case _ =>
        }

      case ce @ ComponentEvent(`balBtnLbl`, _) =>
        val bal =  userWallet.balance()
        push(balBtnLbl.setCaption(bal.toString))
        context.system.scheduler.scheduleOnce(6 seconds, self, ce)

      case ce @ ComponentEvent(`logoutBtn`, _) => push {
        ui.getSession.setAttribute(UnlockClaimView.identityAttr, null)
        ui.getNavigator().navigateTo(UnlockClaimView.name)
      }


      case ClaimBounty(sTx, secret) => Try {

        val tx = sTx.txEntryBytes.toTx
        val adjustedIndex = tx.outs.size - 1
        val ourBounty = tx.outs(adjustedIndex)
        val inIndex = TxIndex(tx.txId, adjustedIndex)
        val in = TxInput(inIndex, ourBounty.amount, SaleSecretDec)
        val out = TxOutput(ourBounty.amount, userWallet.encumberToIdentity())
        val newTx = StandardTx(Seq(in), Seq(out))
        val sig = SaleSecretDec.createUnlockingSignature(newTx.txId, nodeIdentity.tag, nodeIdentity.sign, secret)
        val signedTx = SignedTxEntry(newTx.toBytes, Seq(sig))
        val le = LedgerItem(MessageKeys.BalanceLedger, signedTx.txId, signedTx.toBytes)
        clientEventActor ! BountyTracker(self, userWallet, TxIndex(signedTx.txId, 0),out, le)

      } match {
        case Failure(e) => push(Notification.show(e.getMessage.take(80)))
        case Success(_) =>
      }

      case MessageToSend(to, account, text, amount) => Try {

        val baseTx = createFundedTx(amount)
        val changeTxOut = baseTx.outs.take(1)
        val secret = SeedBytes(1)
        val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(nodeIdentity, account.publicKey, text, secret)
        val paymentOuts = createPaymentOuts(to, secret, amount)

        val tx = userWallet.appendOutputs(baseTx, paymentOuts : _*)

        val signedSTx = signOutputs(tx, secret)
        val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)

        val m : SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toBytes, le.toBytes)

        // TODO watchingMsgSpends += le.txIdHexStr -> WalletUpdate(tx.txId, tx.ins, changeTxOut)

        clientEventActor ! Bag(userWallet, signedSTx, m, WalletUpdate(self, tx.txId, tx.ins, changeTxOut), userId.id)

      } match {
        case Failure(e) => push(Notification.show(e.getMessage.take(80)))
        case Success(_) =>
      }

      case MessageToDelete(index) => inBox.delete(index)
      case MessageToArchive(index) => inBox.archive(index)
      case SentMessageToDelete(index) => inBox.deleteSent(index)
    }

  }

  override def enter(viewChangeEvent: ViewChangeEvent): Unit = {}

}
