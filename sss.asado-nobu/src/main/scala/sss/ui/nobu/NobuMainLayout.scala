package sss.ui.nobu

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.navigator.{View, ViewBeforeLeaveEvent}

import com.vaadin.shared.Registration
import com.vaadin.ui.{Notification, UI}
import sss.ancillary.Logging
import sss.asado.Send
import sss.asado.account.NodeIdentity
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.message.MessageInBox.MessagePage
import sss.asado.message._
import sss.asado.network.MessageEventBus
import sss.asado.state.HomeDomain
import sss.asado.wallet.Wallet
import sss.db.Db
import sss.ui.design.NobuMainDesign
import sss.ui.nobu.NobuUI.Logout
import sss.ui.nobu.UIActor.{TrackLodgements, UnTrackLodgements}

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */

object NobuMainLayout {

  type ShowWrite = (Option[String], String) => Unit


}

class NobuMainLayout(
                     userDir: UserDirectory,
                     userWallet: Wallet,
                     userId: NodeIdentity
                     ) (
                    implicit actorSystem: ActorSystem,
                    val ui: UI,
                    db: Db,
                    identityService: IdentityServiceQuery,
                    conf: Config,
                    homeDomain: HomeDomain,
                    currentBlockHeight: () => Long,
                    messageEventBus: MessageEventBus,
                    send: Send,
                    chainId: GlobalChainIdMask
) extends NobuMainDesign with View with LayoutHelper with Logging {

  val name = "main"
  private implicit val nodeIdentity = userId

  statusButton.setVisible(false)
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


  private def showWrite(to: Option[String] = None, text: String = ""): Unit = {
    itemPanelVerticalLayout.removeAllComponents()
    itemPanelVerticalLayout.addComponent(new WriteLayout(showInBox, userId.id, to, text, userDir))
  }

  writeBtn.addClickListener( _ => showWrite())

  logoutBtn.addClickListener(_ => {
    ui.getSession.setAttribute(NobuUI.SessionAttr, null)
    ui.getNavigator().navigateTo(UnlockClaimView.name)
    messageEventBus publish Logout(userId.id)
  })

  private lazy val inBox = MessageInBox(userId.id)

  def initInBoxPager = inBox.inBoxPager(4)
  def initSentPager = inBox.sentPager(4)
  def initArchivedPager = inBox.archivedPager(4)
  def initJunkPager = inBox.junkPager(4)

  def showBalance: Unit = {
    val bal =  userWallet.balance()
    balBtnLbl.setCaption(bal.toString)
  }

  def showInBox = {
    updatePagingAreas(initInBoxPager)
  }

  balanceCptnBtn.setCaption(userId.id)

  balBtnLbl addClickListener( _ => showBalance)
  archiveBtn addClickListener( _ => updatePagingAreas(initArchivedPager))
  junkBtn addClickListener( _ => updatePagingAreas(initJunkPager))
  inBoxBtn addClickListener( _ => updatePagingAreas(initInBoxPager))
  sentBtn addClickListener(_ => updatePagingAreas(initSentPager))

  showBalance
  showInBox
  setSizeFull

  private def updatePagingAreas(pager: MessagePage[_], isForDeletion: Boolean = false): Unit = {
    prevBtn.setEnabled(pager.hasNext)
    nextBtn.setEnabled(pager.hasPrev)
    itemPanelVerticalLayout.removeAllComponents


    val registrationNext = nextBtn addClickListener (_ => {
      Option(nextBtn.getData) foreach ( _.asInstanceOf[Registration].remove)
      if (pager.hasPrev) {
        val nextPager = pager.prev
        Try(updatePagingAreas(nextPager)) recover {
          case e => Notification.show(e.getMessage)
        }
      }
    })

    nextBtn setData registrationNext

    val registrationPrev = prevBtn addClickListener (_ => {
      Option(prevBtn.getData) foreach (_.asInstanceOf[Registration].remove)
      if (pager.hasNext) {
        val nextPager = pager.next
        Try(updatePagingAreas(nextPager)) recover {
          case e => Notification.show(e.getMessage)
        }
      }
    })

    prevBtn setData registrationPrev

    pager.messages.reverse foreach {

      case msg @ Message(_, _, msgPayload, _, _, _) if(isForDeletion) =>
        val tried = Try(MessagePayloadDecoder.decode.apply(msgPayload))
        if(tried.isSuccess) {
          itemPanelVerticalLayout.addComponent(new DeleteMessageComponent(itemPanelVerticalLayout,
            showWrite,
            inBox,
            msg))
        }


      case msg @ Message(_, _, msgPayload, _, _, _) =>
        val res = Try {
          val a = Try(MessagePayloadDecoder.decode.apply(msgPayload))
          val b = Try(PayloadDecoder.decode.apply(msgPayload))
          if(a.isSuccess) {
            val newC = new NewMessageComponent(itemPanelVerticalLayout,
              showWrite,
              inBox,
              msg)
            itemPanelVerticalLayout.addComponent(newC)

          }
        }

        res match {
          case Failure(e) => log.warn(e.toString)
          case Success(_) =>
      }

      case msg: SavedAddressedMessage =>
        itemPanelVerticalLayout.addComponent(new SentMessageComponent(itemPanelVerticalLayout,
          showWrite,
          inBox,
          msg))
      }

  }


  override def enter(viewChangeEvent: ViewChangeEvent): Unit = {
    messageEventBus publish TrackLodgements(sessId, userId.id, () => showBalance)
    showBalance
  }

  override def beforeLeave(event: ViewBeforeLeaveEvent): Unit = {
    messageEventBus publish UnTrackLodgements(sessId)
    event.navigate()
  }
}
