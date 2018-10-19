package sss.ui.nobu


import akka.actor.{ActorRef, Props}
import com.typesafe.config.Config
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui.Button.ClickEvent
import com.vaadin.ui._
import org.joda.time.LocalDateTime
import sss.ancillary.Logging
import sss.asado.account.{NodeIdentity, NodeIdentityManager, PublicKeyAccount}
import sss.asado.identityledger.IdentityService
import sss.asado.message.{Message, MessageInBox}
import sss.asado.state.HomeDomain
import sss.asado.wallet.{Wallet, WalletPersistence}
import sss.ui.design.CenteredAccordianDesign
import sss.ui.reactor.{ComponentEvent, UIReactor}
import sss.asado.util.ByteArrayEncodedStrOps.ByteArrayToBase64UrlStr
import sss.db.Db
import sss.ui.nobu.Main.ClientNode

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
                      currentBlockHeight: () => Long
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

    override def react(reactor: ActorRef, broadcaster: ActorRef, ui: UI) = {

      case ComponentEvent(`claimBtnVal`, _) =>

        val claim = claimIdentityText.getValue
        val claimTag = claimTagText.getValue

        if(nodeIdentityManager.keyExists(claim, claimTag)) {
          push(Notification.show(s"Identity $claim exists, try loading it instead?"))
        } else {
          Try {
            val phrase = claimPhrase.getValue
            val phraseRetype = claimPhraseRetype.getValue
            if(phrase != phraseRetype) throw new Error(s"The passwords do not match!")
            else {
              if(identityService.accounts(claim).nonEmpty) throw new Error(s"Identity already claimed!")
              else {
                val nId = nodeIdentityManager(claim, claimTag, phrase)
                val publicKey = nId.publicKey.toBase64Str
                val message = Message(claim, msgPayload =
                  IdentityClaimMessagePayload(claim, claimTag, nId.publicKey, claimInfoTextArea.getValue).toMessagePayLoad,
                  tx = Array(),
                  index = 0,
                  createdAt = new LocalDateTime)
                MessageInBox(userDir.administrator).addNew(message)
                push(Notification.show(s"Thank you $claim, your registration CREATE D NEW MESSA"))
              }
            }
          } match {
            case Failure(e) => push(Notification.show(s"${e.getMessage}", Notification.Type.ERROR_MESSAGE))
            case Success(_) =>
              push(Notification.show(s"Thank you $claim, your registration has been successfully submitted"))
          }
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

            case Success(nId) => gotoMainView(nId)
          }
        }
    }

    def createWallet(nId: NodeIdentity) : Wallet = {
      buildWallet(nId)
    }

    def gotoMainView(nId: NodeIdentity): Unit = {
      val userWallet = createWallet(nId)
      getSession().setAttribute(UnlockClaimView.identityAttr, nId.id)
      UserSession.note(nId, userWallet)
      val mainView = new NobuMainLayout(uiReactor, userDir, userWallet, nId)
      push {
        getUI().getNavigator.addView(mainView.name, mainView)
        getUI().getNavigator.navigateTo(mainView.name)
      }
    }
  }
}


object UnlockClaimView {
  val name = "unlockClaimView"
  val identityAttr = "nodeIdentity"
}