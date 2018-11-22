package sss.ui.nobu


import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.server.VaadinSession
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
import sss.db.Db
import sss.ui.Servlet
import sss.ui.nobu.BlockingWorkers.BlockingTask
import sss.ui.nobu.CreateIdentity.{ClaimIdentity, Fund, Funded}
import sss.ui.nobu.UIActor.TrackSessionRef

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
                      val ui: UI,
                      send: Send,
                      nodeIdentityManager: NodeIdentityManager,
                      identityService: IdentityService,
                      homeDomain: HomeDomain,
                      db:Db,
                      conf:Config,
                      currentBlockHeight: () => Long,
                      messageEventBus: MessageEventBus,
                      chainId: GlobalChainIdMask
                      ) extends CenteredAccordianDesign
  with View
  with LayoutHelper
  with Notifications
  with Logging {

  private val claimBtnVal = claimBtn
  private val unlockBtnVal = unlockBtn

  unlockTagText.setVisible(false)
  claimTagText.setVisible(false)
  //NB MUST BE DEFAULT TAG
  claimTagText.setValue("defaultTag")

  identityCombo.setEmptySelectionAllowed(true)

  unlockInfoTextArea.setRows(8)
  claimInfoTextArea.setRows(8)


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

  claimBtnVal.addClickListener(_ => {
    val claim = claimIdentityText.getValue
    if(!IdentityService.validateIdentity(claim)) {
      showWarn(s"Lowercase with no spaces only please.")
    } else {
      val claimTag = claimTagText.getValue
      val phrase = claimPhrase.getValue
      val phraseRetype = claimPhraseRetype.getValue
      if (phrase != phraseRetype) showWarn(s"The passwords do not match!")
      else {
        messageEventBus publish BlockingTask(ClaimIdentity(claim, claimTag, phrase))
        navigator.navigateTo(WaitKeyGenerationView.name)
      }
    }
  })


  unlockBtnVal.addClickListener(_ => {
    Option(identityCombo.getValue) map { idTag =>
      val claimAndTag = IdTagValue(idTag.toString)
      val tag = claimAndTag.tag
      val identity = claimAndTag.identity
      val phrase = unLockPhrase.getValue
      Try(nodeIdentityManager(identity, tag, phrase)) match {
        case Failure(e) =>
          log.error("Failed to unlock {} {}", identity, e)
          show(s"${e.getMessage}")

        case Success(nodeIdentity) =>
          val userWallet = buildWallet(nodeIdentity)
          Option(ui.getSession()) match {
            case Some(sess) =>

              val mainView: NobuMainLayout = setupMainLayout(nodeIdentity, userWallet, sess)
              ui.getNavigator.navigateTo(mainView.name)
            case None =>
              log.error("How can there be no session?")

          }
      }
    }
  })

  private def setupMainLayout(nodeIdentity: NodeIdentity, userWallet: Wallet, sess: VaadinSession) = {
    val msgDownRef = MessageDownloadActor(ValidateBounty.validateBounty, nodeIdentity, userWallet, homeDomain)
    msgDownRef ! CheckForMessages

    messageEventBus publish TrackSessionRef(sessId, msgDownRef)
    UserSession.note(nodeIdentity, userWallet)
    sess.setAttribute(NobuUI.SessionAttr, nodeIdentity.id)
    val mainView = new NobuMainLayout(userDir, userWallet, nodeIdentity)
    ui.getNavigator.addView(mainView.name, mainView)
    mainView
  }

  override def enter(viewChangeEvent: ViewChangeEvent): Unit = {
    Option(getSession().getAttribute(NobuUI.SessionAttr)) match {
      case Some(loggedIn: String) =>
        UserSession(loggedIn) foreach { us =>
          val mainView = new NobuMainLayout(userDir, us.userWallet, us.nodeId)
          navigator.addView(mainView.name, mainView)
          navigator.navigateTo(mainView.name)
        }
      case _ =>
        val keyNames = userDir.listUsers
        if(keyNames.isEmpty) showClaim
        else showUnlock
    }
  }

}


object UnlockClaimView {
  val name = "unlockClaimView"
}