package sss.ui.nobu

import akka.actor.{ActorRef, Props}
import com.typesafe.config.Config
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.server.FontAwesome
import com.vaadin.ui.{Notification, UI}
import sss.ancillary.Logging
import sss.asado.MessageKeys
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{StandardTx, TxIndex, TxInput, TxOutput, _}
import sss.asado.chains.BlockChainSettings
import sss.asado.contract.{SaleOrReturnSecretEnc, SaleSecretDec, SingleIdentityEnc}
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.message.MessageInBox.MessagePage
import sss.asado.message.{Message, MessageEcryption, MessageInBox, SavedAddressedMessage, _}
import sss.asado.state.HomeDomain
import sss.asado.wallet.Wallet
import sss.db.Db
import sss.ui.design.NobuMainDesign
import sss.ui.nobu.Main.ClientNode
import sss.ui.nobu.NobuNodeBridge._
import sss.ui.reactor.{ComponentEvent, Register, UIEventActor, UIReactor}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

/**
  * Created by alan on 6/10/16.
  */
case class ShowWrite(to: String = "", text: String = "")
case class Show(s: String)
case object ShowBalance
case object ShowSchedules

case class Bag(userWallet: Wallet, sTx: SignedTxEntry, msg: SavedAddressedMessage, walletUpdate: WalletUpdate, from: String)

class NobuMainLayout(uiReactor: UIReactor,
                     userDir: UserDirectory,
                     userWallet: Wallet,
                     userId: NodeIdentity
                     ) (
                    implicit db: Db,
                    identityService: IdentityServiceQuery,
                    conf: Config, homeDomain: HomeDomain,
                    currentBlockHeight: () => Long
) extends NobuMainDesign with View with Logging {


  private implicit val nodeIdentity = userId
  private val msgDecoders = MessagePayloadDecoder.decode orElse PayloadDecoder.decode

  private lazy val minNumBlocksInWhichToClaim = conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  private lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")
  private lazy val amountBuriedInMail = conf.getInt("messagebox.amountBuriedInMail")

  schedulesButton.setIcon(FontAwesome.CALENDAR_CHECK_O)

  statusButton.setVisible(false)
  settingsButton.setVisible(false)

  val name = "main"

  val schedulesBtn = schedulesButton
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
  schedulesBtn.addClickListener(uiReactor)
  archiveBtn.addClickListener(uiReactor)
  sentBtn.addClickListener(uiReactor)
  logoutBtn.addClickListener(uiReactor)
  nextBtn.addClickListener(uiReactor)
  prevBtn.addClickListener(uiReactor)
  balBtnLbl.addClickListener(uiReactor)



  val mainNobuRef = uiReactor.actorOf(Props(NobuMainActor),
    logoutButton, inBoxBtn, writeBtn, archiveBtn, sentBtn, nextBtn, prevBtn, balBtnLbl, schedulesBtn)

  mainNobuRef ! Register(NobuNodeBridge.NobuCategory)

  private lazy val nId = userId.id
  private lazy val inBox = MessageInBox(nId)

  balanceCptnBtn.setCaption(nId)

  mainNobuRef ! ShowInBox
  mainNobuRef ! ShowBalance

  private def updatePagingAreas(pager: MessagePage[_], isForDeletion: Boolean = false): Unit = {
    prevBtn.setEnabled(pager.hasNext)
    nextBtn.setEnabled(pager.hasPrev)
    itemPanelVerticalLayout.removeAllComponents

    pager.messages.reverse foreach {

      case msg @ Message(_, msgPayload, _, _, _) if(isForDeletion) =>
        val tried = Try(MessagePayloadDecoder.decode.apply(msgPayload))
        if(tried.isSuccess) {
          itemPanelVerticalLayout.addComponent(new DeleteMessageComponent(itemPanelVerticalLayout,
            mainNobuRef, msg))
        }


      case msg @ Message(_, msgPayload, _, _, _) =>
        val res = Try {
          val a = Try(MessagePayloadDecoder.decode.apply(msgPayload))
          val b = Try(PayloadDecoder.decode.apply(msgPayload))
          if(a.isSuccess) {
            val newC = new NewMessageComponent(itemPanelVerticalLayout,
              mainNobuRef, msg)
            itemPanelVerticalLayout.addComponent(newC)

          } else if(b.isSuccess) {
            itemPanelVerticalLayout.addComponent(new IdentityClaimComponent(itemPanelVerticalLayout,
              mainNobuRef, msg, homeDomain, b.get.asInstanceOf[IdentityClaimMessagePayload]))
          }

          /*val newMsg = msgDecoders(msgPayload) match {
            case EncryptedMessage(enc, iv) =>
              new NewMessageComponent(itemPanelVerticalLayout,
                mainNobuRef, msg)
            case idClaim @ IdentityClaimMessagePayload(claimedIdentity, tag, publicKey, supportingText) =>
              new IdentityClaimComponent(itemPanelVerticalLayout,
                mainNobuRef, msg, nobuNode.homeDomain, idClaim)
            case x => log.warn("WHAT?"); throw new RuntimeException("")
          }
          itemPanelVerticalLayout.addComponent(newMsg)*/

        }

        res match {
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
        TxOutput(chargePerMessage, SingleIdentityEnc(homeDomain.nodeId.id)),
        TxOutput(amount, SaleOrReturnSecretEnc(nodeIdentity.id, to, secret,
          currentBlockHeight() + minNumBlocksInWhichToClaim + 4))
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
        itemPanelVerticalLayout.addComponent(new WriteLayout(mainNobuRef, to, text, userDir))
      }

      case ShowSchedules => push {
        itemPanelVerticalLayout.removeAllComponents()
        itemPanelVerticalLayout.addComponent(new ScheduleLayout(mainNobuRef, userDir, userId.id))
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

      case ComponentEvent(`schedulesBtn`, _) =>
        self ! ShowSchedules

      case ComponentEvent(`inBoxBtn`, _) =>
        self ! ShowInBox

      case ComponentEvent(`archiveBtn`, _) =>
        pager = initArchivedPager
        Try(push(updatePagingAreas(pager, isForDeletion = true)))  match {
          case Failure(e) => push(Notification.show(e.getMessage))
          case _ =>
        }

      case ce @ ComponentEvent(`balBtnLbl`, _) => self ! ShowBalance


      case ce @ ComponentEvent(`logoutBtn`, _) => push {
        ui.getSession.setAttribute(UnlockClaimView.identityAttr, null)
        ui.getNavigator().navigateTo(UnlockClaimView.name)
      }


      case ShowBalance =>
        val bal =  userWallet.balance()
        push(balBtnLbl.setCaption(bal.toString))
        context.system.scheduler.scheduleOnce(4 seconds, self, ShowBalance)

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
        // FIXME clientEventActor ! BountyTracker(self, userWallet, TxIndex(signedTx.txId, 0),out, le)

      } match {
        case Failure(e) => push(Notification.show(e.getMessage.take(80)))
        case Success(_) =>
      }

      case MessageToSend(to, account, text, amount) => Try {

        log.info("MessageToSend begins")
        val baseTx = createFundedTx(amount)
        val changeTxOut = baseTx.outs.take(1)
        val secret = new Array[Byte](8) //SeedBytes(16)
        Random.nextBytes(secret)

        val encryptedMessage = MessageEcryption.encryptWithEmbeddedSecret(nodeIdentity, account.publicKey, text, secret)
        val paymentOuts = createPaymentOuts(to, secret, amount)
        val tx = userWallet.appendOutputs(baseTx, paymentOuts : _*)
        val signedSTx = signOutputs(tx, secret)
        val le = LedgerItem(MessageKeys.BalanceLedger, signedSTx.txId, signedSTx.toBytes)
        val m : SavedAddressedMessage = inBox.addSent(to, encryptedMessage.toMessagePayLoad, le.toBytes)
        val bag = Bag(userWallet, signedSTx, m, WalletUpdate(self, tx.txId, tx.ins, changeTxOut), userId.id)

        /*val msg = Message(bag.from,
          bag.msg.addrMsg.msgPayload,
          bag.sTx.toBytes,
          0, bag.msg.savedAt)

        val newMsg = MessageInBox(bag.msg.to).addNew(msg)
        val msgP = newMsg.msgPayload
        val enc1 = MessageEcryption.encryptedMessage(msgP.payload)
        val n = NodeIdentity(to, "defaultTag", "password")
        val twithsec = enc1.decrypt(n, account.publicKey)*/

        // TODO watchingMsgSpends += le.txIdHexStr -> WalletUpdate(tx.txId, tx.ins, changeTxOut)
        log.info("MessageToSend finished, sending bag")
        //FIXME clientEventActor ! bag FIXME

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
