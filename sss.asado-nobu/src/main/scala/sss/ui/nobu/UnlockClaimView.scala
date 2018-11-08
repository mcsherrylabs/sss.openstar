package sss.ui.nobu


import akka.actor.{ActorRef, Props}
import com.typesafe.config.Config
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui._
import sss.ancillary.Logging
import sss.asado.UniqueNodeIdentifier
import sss.asado.account.{NodeIdentity, NodeIdentityManager}
import sss.asado.identityledger.IdentityService
import sss.asado.network.MessageEventBus
import sss.asado.state.HomeDomain
import sss.asado.wallet.UtxoTracker.NewWallet
import sss.asado.wallet.{Wallet, WalletTracking}
import sss.ui.design.CenteredAccordianDesign
import sss.ui.reactor.{ComponentEvent, UIReactor}
import sss.db.Db
import sss.ui.nobu.CreateIdentity.{ClaimIdentity, Fund, NewClaimedIdentity}
import sss.ui.nobu.NobuNodeBridge.Notify
import sss.ui.nobu.NobuUI.Detach

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */

private case class IdTagValue(str: String) {

  val identity: String = str
  val tag: String = "defaultTag"
}

class UnlockClaimView(userDir: UserDirectory,
                      buildWallet: NodeIdentity => Wallet
                     )(
                      implicit uiReactor: UIReactor,
                      nodeIdentityManager: NodeIdentityManager,
                      identityService: IdentityService,
                      homeDomain: HomeDomain,
                      db:Db,
                      conf:Config,
                      currentBlockHeight: () => Long,
                      blockingWorkers: BlockingWorkers,
                      messageEventBus: MessageEventBus
                      ) extends CenteredAccordianDesign with View with Logging {

  private val claimBtnVal = claimBtn
  private val unlockBtnVal = unlockBtn

  unlockTagText.setVisible(false)
  claimTagText.setVisible(false)
  //NB MUST BE DEFAULT TAG
  claimTagText.setValue("defaultTag")

  identityCombo.setNewItemsAllowed(false)
  identityCombo.setNullSelectionAllowed(true)
  unlockInfoTextArea.setRows(8)
  claimInfoTextArea.setRows(8)


  uiReactor.actorOf(Props(UnlockClaimViewActor), claimBtnVal, unlockBtnVal)

  private def showClaim = {
    rhsClaim.setVisible(true)
    rhsUnlock.setVisible(false)
  }

  private def showUnlock = {

    rhsClaim.setVisible(false)
    rhsUnlock.setVisible(true)
    userDir.loadCombo(identityCombo)
  }

  claimMnuBtn.addClickListener(_ => showClaim)

  unlockMnuBtn.addClickListener(_ => showUnlock)

  claimBtnVal.addClickListener(uiReactor)
  unlockBtnVal.addClickListener(uiReactor)

  override def enter(viewChangeEvent: ViewChangeEvent): Unit = {
    val keyNames = userDir.listUsers
    if(keyNames.isEmpty) showClaim
    else showUnlock
  }

  object UnlockClaimViewActor extends sss.ui.reactor.UIEventActor {

    messageEventBus subscribe classOf[Detach]

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI) = {

      case Detach(Some(uiId)) if (ui.getEmbedId == uiId) =>
        context stop self

      case Notify(msg, t) =>
        push(Notification.show(msg, t))

      case NewClaimedIdentity(nId) =>
        self ! Notify(s"Identity ${nId.id} successfully claimed, funding wallet ...")
        messageEventBus publish NewWallet(buildWallet(nId).walletTracker)
        blockingWorkers.submit(Fund(nId.id, self))
        gotoMainView(ui, nId)

      case ComponentEvent(`claimBtnVal`, _) =>

        val claim = claimIdentityText.getValue
        val claimTag = claimTagText.getValue
        val phrase = claimPhrase.getValue
        val phraseRetype = claimPhraseRetype.getValue
        if (phrase != phraseRetype) self ! Notify(s"The passwords do not match!", Notification.Type.ERROR_MESSAGE)
        else blockingWorkers.submit(ClaimIdentity(claim, claimTag, phrase, self))


      case ComponentEvent(`unlockBtnVal`, _) =>
        Option(identityCombo.getValue) map { idTag =>
          val claimAndTag = IdTagValue(idTag.toString)
          val tag = claimAndTag.tag
          val identity = claimAndTag.identity
          val phrase = unLockPhrase.getValue
          Try(nodeIdentityManager(identity, tag, phrase)) match {
            case Failure(e) =>
              log.error("Failed to unlock {} {}", identity, e)
              push(Notification.show(s"${e.getMessage}"))

            case Success(nId) =>
              gotoMainView(ui, nId)
          }
        }
    }

    def createWallet(nId: NodeIdentity) : Wallet = {
      buildWallet(nId)
    }

    def gotoMainView(ui: UI, nId: NodeIdentity): Unit = {
      val userWallet = createWallet(nId)
      getSession().setAttribute(UnlockClaimView.identityAttr, nId.id)
      UserSession.note(nId, userWallet)
      val mainView = new NobuMainLayout(uiReactor, userDir, userWallet, nId)
      push {
        ui.getNavigator.addView(mainView.name, mainView)
        ui.getNavigator.navigateTo(mainView.name)
      }
    }
  }
}


object UnlockClaimView {
  val name = "unlockClaimView"
  val identityAttr = "nodeIdentity"
}