package sss.ui.nobu


import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui._
import sss.ancillary.Logging
import sss.asado.{Send, UniqueNodeIdentifier}
import sss.asado.account.{NodeIdentity, NodeIdentityManager}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.identityledger.IdentityService
import sss.asado.message.MessageDownloadActor
import sss.asado.message.MessageDownloadActor.CheckForMessages
import sss.asado.network.MessageEventBus
import sss.asado.state.HomeDomain
import sss.asado.wallet.UtxoTracker.NewWallet
import sss.asado.wallet.{Wallet, WalletIndexTracker}
import sss.ui.design.CenteredAccordianDesign
import sss.ui.reactor.{ComponentEvent, UIReactor}
import sss.db.Db
import sss.ui.Servlet
import sss.ui.nobu.CreateIdentity.{ClaimIdentity, Fund, Funded, NewClaimedIdentity}
import sss.ui.nobu.NobuNodeBridge.{Fail, Notify}
import sss.ui.nobu.NobuUI.Detach
import sss.ui.nobu.WaitKeyGenerationView.Update

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
                      implicit actorSystem:ActorSystem,
                      send: Send,
                      uiReactor: UIReactor,
                      nodeIdentityManager: NodeIdentityManager,
                      identityService: IdentityService,
                      homeDomain: HomeDomain,
                      db:Db,
                      conf:Config,
                      currentBlockHeight: () => Long,
                      blockingWorkers: BlockingWorkers,
                      messageEventBus: MessageEventBus,
                      chainId: GlobalChainIdMask
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


  val ref = uiReactor.actorOf(Props(UnlockClaimViewActor), claimBtnVal, unlockBtnVal)

  messageEventBus.subscribe (classOf[Detach])(ref)

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
    Option(getSession().getAttribute(Servlet.SessionAttr)) match {
      case None =>
        val keyNames = userDir.listUsers
        if(keyNames.isEmpty) showClaim
        else showUnlock
      case Some(loggedIn: String) =>
        UserSession(loggedIn) foreach { us =>
          val mainView = new NobuMainLayout(uiReactor, userDir, us.userWallet, us.nodeId)
          getUI().getNavigator.addView(mainView.name, mainView)
          getUI().getNavigator.navigateTo(mainView.name)
        }

    }

  }

  object UnlockClaimViewActor extends sss.ui.reactor.UIEventActor {

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI) = {

      case Detach(Some(uiId)) if (ui.getEmbedId == uiId) =>
        context stop self

      case Fail(m) =>
        push(Notification.show(m, Notification.Type.ERROR_MESSAGE))
        push(ui.getNavigator.navigateTo(UnlockClaimView.name))

      case Notify(msg, t) =>
        push(Notification.show(msg, t))

      case NewClaimedIdentity(nId) =>
        messageEventBus publish NewWallet(buildWallet(nId).walletTracker)
        blockingWorkers.submit(Fund(Option(ui.getEmbedId), nId, self))

      case Funded(Some(uiId), nId, amount) =>
        gotoMainView(ui, nId)

      case ComponentEvent(`claimBtnVal`, _) =>

        val claim = claimIdentityText.getValue
        val claimTag = claimTagText.getValue
        val phrase = claimPhrase.getValue
        val phraseRetype = claimPhraseRetype.getValue
        if (phrase != phraseRetype) self ! Notify(s"The passwords do not match!", Notification.Type.ERROR_MESSAGE)
        else {
          blockingWorkers.submit(ClaimIdentity(Option(ui.getEmbedId), claim, claimTag, phrase, self))
          push(ui.getNavigator.navigateTo(WaitKeyGenerationView.name))
        }


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


    /*
    Trivial acceptance of messages if the amount is greater than 0
    no matter who they are from
     */
    def validateBounty(amount: Long, from: UniqueNodeIdentifier): Boolean = amount > 0


    def gotoMainView(ui: UI, nId: NodeIdentity): Unit = {
      val userWallet = createWallet(nId)
      Option(ui.getSession()) match {
        case Some(sess) =>

          //TODO Make This actor die on session expiry.
          MessageDownloadActor(validateBounty, nId, userWallet, homeDomain) ! CheckForMessages

          UserSession.note(nId, userWallet)
          sess.setAttribute(Servlet.SessionAttr, nId.id)
          val mainView = new NobuMainLayout(uiReactor, userDir, userWallet, nId)
          push {
            ui.getNavigator.addView(mainView.name, mainView)
            ui.getNavigator.navigateTo(mainView.name)
          }
        case None =>
          log.error("Couldn't get ui session?")
      }
    }
  }
}


object UnlockClaimView {
  val name = "unlockClaimView"
}