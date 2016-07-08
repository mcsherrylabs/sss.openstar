package sss.ui.nobu

import akka.actor.{ActorRef, Props}
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui.{Notification, UI}
import sss.asado.account.NodeIdentity
import sss.asado.identityledger.IdentityServiceQuery
import sss.asado.message.MessageInBox.MessagePage
import sss.asado.message.{AddressedMessage, CheckForMessages, ForceCheckForMessages, Message, MessageInBox, SavedAddressedMessage}
import sss.db.Db
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import sss.ui.design.NobuMainDesign
import sss.ui.nobu.NobuNodeBridge.ShowInBox
import sss.ui.reactor.{ComponentEvent, Register, UIEventActor, UIReactor}

/**
  * Created by alan on 6/10/16.
  */
case class ShowWrite(to: String = "", text: String = "")

class NobuMainLayout(uiReactor: UIReactor, nobuNode: NobuNode) extends NobuMainDesign with View  {

  private implicit val db: Db = nobuNode.db
  private implicit val identityQuery: IdentityServiceQuery = nobuNode.identityService
  private implicit val nodeIdentity: NodeIdentity = nobuNode.nodeIdentity

  statusButton.setVisible(false)
  logoutButton.setVisible(false)
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

  private lazy val nId = nodeIdentity.id
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
          mainNobuRef, uiReactor, msg))

      case msg: Message =>
        itemPanelVerticalLayout.addComponent(new NewMessageComponent(itemPanelVerticalLayout,
          mainNobuRef, uiReactor, msg))

      case msg: SavedAddressedMessage =>
        itemPanelVerticalLayout.addComponent(new SentMessageComponent(itemPanelVerticalLayout,
          mainNobuRef, uiReactor, msg))
      }

  }



  setSizeFull

  object NobuMainActor extends UIEventActor {

    def initInBoxPager = inBox.inBoxPager(4)
    def initSentPager = inBox.sentPager(4)
    def initArchivedPager = inBox.archivedPager(4)

    var pager: MessagePage[_] = initInBoxPager

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI): Receive = {

      case ShowWrite(to, text) => push {
        itemPanelVerticalLayout.removeAllComponents()
        itemPanelVerticalLayout.addComponent(new WriteLayout(uiReactor, to, text))
      }

      case ShowInBox =>
        pager = initInBoxPager
        nobuNode.messageDownloaderActor ! ForceCheckForMessages
        push(updatePagingAreas(pager))

      case ComponentEvent(`prevBtn`, _) =>
        if(pager.hasNext) {
          pager = pager.next
          push(updatePagingAreas(pager))
        }

      case ComponentEvent(`nextBtn`, _) =>
        if(pager.hasPrev) {
          pager = pager.prev
          push(updatePagingAreas(pager))
        }

      case ComponentEvent(`writeBtn`, _) =>
        self ! ShowWrite()

      case ComponentEvent(`sentBtn`, _) =>
        pager = initSentPager
        push(updatePagingAreas(pager))

      case ComponentEvent(`inBoxBtn`, _) =>
        self ! ShowInBox

      case ComponentEvent(`archiveBtn`, _) =>
        pager = initArchivedPager
        push(updatePagingAreas(pager, isForDeletion = true))

      case ce @ ComponentEvent(`balBtnLbl`, _) =>
        val bal =  nobuNode.wallet.balance()
        push(balBtnLbl.setCaption(bal.toString))
        context.system.scheduler.scheduleOnce(3 seconds, self, ce)
    }

  }

  override def enter(viewChangeEvent: ViewChangeEvent): Unit = {}

}
