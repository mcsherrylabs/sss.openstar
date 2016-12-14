package sss.ui.nobu




import akka.actor.{ActorRef, Props}
import com.vaadin.navigator.View
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent
import com.vaadin.ui.Button.ClickEvent
import com.vaadin.ui.{Button, Notification, UI}
import org.joda.time.LocalDateTime
import sss.ancillary.Logging
import sss.asado.account.NodeIdentity
import sss.asado.message.{Message, MessageInBox}
import sss.asado.nodebuilder.ClientNode
import sss.asado.util.ByteArrayEncodedStrOps._
import sss.asado.wallet.{Wallet, WalletPersistence}
import sss.ui.design.CenteredAccordianDesign
import sss.ui.reactor.{ComponentEvent, UIReactor}

import scala.util.{Failure, Success, Try}

/**
  * Created by alan on 6/10/16.
  */

private case class IdTagValue(str: String) {
  //private val tuple = str.split(", ")
  val identity: String = str
  val tag: String = "defaultTag"
}

class UnlockClaimView(
                      uiReactor: UIReactor,
                      userDir: UserDirectory,
                      clientNode: ClientNode,
                      clientEventActor: ActorRef
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

  import clientNode.db

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

  claimMnuBtn.addClickListener(new Button.ClickListener{
    override def buttonClick(event: ClickEvent): Unit = showClaim
  })

  unlockMnuBtn.addClickListener(new Button.ClickListener{
    override def buttonClick(event: ClickEvent): Unit = showUnlock
  })

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

        if(NodeIdentity.keyExists(claim, claimTag)) {
          push(Notification.show(s"Identity $claim exists, try loading it instead?"))
        } else {
          Try {
            val phrase = claimPhrase.getValue
            val phraseRetype = claimPhraseRetype.getValue
            if(phrase != phraseRetype) throw new Error(s"The passwords do not match!")
            else {
              if(clientNode.identityService.accounts(claim).nonEmpty) throw new Error(s"Identity already claimed!")
              else {
                val nId = NodeIdentity(claim, claimTag, phrase)
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
          Try(NodeIdentity(identity, tag, phrase)) match {
            case Failure(e) =>
              log.error("Failed to unlock {} {}", identity, e)
              push(Notification.show(s"${e.getMessage}"))

            case Success(nId) => gotoMainView(nId)
          }
        }
    }

    def createWallet(nId: NodeIdentity) : Wallet = {
      new Wallet(nId,
        clientNode.balanceLedger,
        clientNode.identityService,
        new WalletPersistence(nId.id, clientNode.db),
        clientNode.currentBlockHeight _)
    }

    def gotoMainView(nId: NodeIdentity): Unit = {
      val userWallet = createWallet(nId)
      getSession().setAttribute(UnlockClaimView.identityAttr, nId.id)
      val mainView = new NobuMainLayout(uiReactor, userDir, userWallet, nId, clientNode, clientEventActor)
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