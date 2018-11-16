package sss.ui.nobu

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui.{Notification, UI}
import sss.ancillary.Logging
import sss.asado.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{StandardTx, TxIndex, TxInput, TxOutput, _}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.chains.TxWriterActor._
import sss.asado.contract.SaleSecretDec
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.ledger.{LedgerItem, SignedTxEntry}
import sss.asado.message.MessageDownloadActor.{CheckForMessages, NewInBoxMessage}
import sss.asado.message.MessageInBox.MessagePage
import sss.asado.message._
import sss.asado.network.MessageEventBus
import sss.asado.network.MessageEventBus.IncomingMessage
import sss.asado.state.HomeDomain

import sss.asado.wallet.Wallet
import sss.db.Db
import sss.ui.Servlet
import sss.ui.design.NobuMainDesign
import sss.ui.nobu.NobuMainLayout.{Show, ShowBalance, ShowWrite}
import sss.ui.nobu.NobuNodeBridge._
import sss.ui.nobu.NobuUI.Detach


import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */

object NobuMainLayout {
  case class ShowWrite(to: String = "", text: String = "")
  case class Show(s: String)
  case object ShowBalance

}
class NobuMainLayout(
                     userDir: UserDirectory,
                     userWallet: Wallet,
                     userId: NodeIdentity
                     ) (
                    implicit actorSystem: ActorSystem,
                    db: Db,
                    identityService: IdentityServiceQuery,
                    conf: Config,
                    homeDomain: HomeDomain,
                    currentBlockHeight: () => Long,
                    messageEventBus: MessageEventBus,
                    send: Send,
                    chainId: GlobalChainIdMask
) extends NobuMainDesign with View with Logging {

  val name = "main"
  private implicit val nodeIdentity = userId
  //private val msgDecoders = MessagePayloadDecoder.decode orElse PayloadDecoder.decode

  //private lazy val minNumBlocksInWhichToClaim = conf.getInt("messagebox.minNumBlocksInWhichToClaim")
  //private lazy val chargePerMessage = conf.getInt("messagebox.chargePerMessage")
  //private lazy val amountBuriedInMail = conf.getInt("messagebox.amountBuriedInMail")

  /*statusButton.setVisible(false)
  settingsButton.setVisible(false)



  val inBoxBtn = inboxButton
  val writeBtn = writeButton
  val archiveBtn = archiveButton
  val junkBtn = junkButton
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
  junkBtn.addClickListener(uiReactor)



  val mainNobuRef = uiReactor.actorOf(Props(NobuMainActor),
    logoutButton, inBoxBtn, writeBtn, archiveBtn, junkBtn, sentBtn, nextBtn, prevBtn, balBtnLbl)

  mainNobuRef ! Register(NobuNodeBridge.NobuCategory)
  messageEventBus.subscribe (classOf[Detach])(mainNobuRef)
  messageEventBus.subscribe (classOf[NewLodgement])(mainNobuRef)
  messageEventBus.subscribe (MessageKeys.MessageResponse)(mainNobuRef)
  messageEventBus.subscribe (classOf[NewInBoxMessage])(mainNobuRef)

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

      case msg @ Message(_, _, msgPayload, _, _, _) if(isForDeletion) =>
        val tried = Try(MessagePayloadDecoder.decode.apply(msgPayload))
        if(tried.isSuccess) {
          itemPanelVerticalLayout.addComponent(new DeleteMessageComponent(itemPanelVerticalLayout,
            mainNobuRef, msg))
        }


      case msg @ Message(_, _, msgPayload, _, _, _) =>
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
        }

        res match {
          case Failure(e) => log.warn(e.toString)
          case Success(_) =>
      }

      case msg: SavedAddressedMessage =>
        itemPanelVerticalLayout.addComponent(new SentMessageComponent(itemPanelVerticalLayout,
          mainNobuRef, msg))
      }

  }*/


  setSizeFull

  /*object NobuMainActor extends UIEventActor {


    def initInBoxPager = inBox.inBoxPager(4)
    def initSentPager = inBox.sentPager(4)
    def initArchivedPager = inBox.archivedPager(4)
    def initJunkPager = inBox.junkPager(4)

    var bounties: Map[Long, String] = Map()

    var pager: MessagePage[_] = initInBoxPager

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

      case Detach(Some(uiId)) if (ui.getEmbedId == uiId) =>
        context stop self


      case Show(s) => push(Notification.show(s))

      case ShowWrite(to, text) => push {
        itemPanelVerticalLayout.removeAllComponents()
        itemPanelVerticalLayout.addComponent(new WriteLayout(mainNobuRef, userId.id, to, text, userDir))
      }


      case _: NewInBoxMessage =>
        self ! ShowInBox

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

      case ComponentEvent(`junkBtn`, _) =>
        pager = initJunkPager
        Try(push(updatePagingAreas(pager)))  match {
          case Failure(e) => push(Notification.show(e.getMessage))
          case _ =>
        }

      case ComponentEvent(`archiveBtn`, _) =>
        pager = initArchivedPager
        Try(push(updatePagingAreas(pager, isForDeletion = true)))  match {
          case Failure(e) => push(Notification.show(e.getMessage))
          case _ =>
        }

      case ce @ ComponentEvent(`balBtnLbl`, _) => self ! ShowBalance


      case ce @ ComponentEvent(`logoutBtn`, _) => push {
        ui.getSession.setAttribute(Servlet.SessionAttr, null)
        ui.getNavigator().navigateTo(UnlockClaimView.name)
        import context.dispatcher
        context.system.scheduler.scheduleOnce(4 seconds, self, {ui.close()})
      }

      case NewLodgement(`nId`, _) => self ! ShowBalance

      case ShowBalance =>
        val bal =  userWallet.balance()
        push(balBtnLbl.setCaption(bal.toString))
        //context.system.scheduler.scheduleOnce(4 seconds, self, ShowBalance)

      case IncomingMessage(`chainId`, MessageKeys.MessageResponse, _, _:SuccessResponse) =>
        self ! Show(s"Message away!")

      case IncomingMessage(`chainId`, MessageKeys.MessageResponse, _, FailureResponse(_, info)) =>
        self ! Show(s"Failed to send message - $info")

      case MessageToDelete(index) => inBox.delete(index)
      case MessageToArchive(index) => inBox.archive(index)
      case SentMessageToDelete(index) => inBox.deleteSent(index)
    }

  }*/

  override def enter(viewChangeEvent: ViewChangeEvent): Unit = {}

}
