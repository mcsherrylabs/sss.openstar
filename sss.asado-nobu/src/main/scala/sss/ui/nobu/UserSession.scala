package sss.ui.nobu

import com.vaadin.server.VaadinSession
import sss.asado.account.NodeIdentity
import sss.asado.wallet.Wallet

/**
  * Created by alan on 12/20/16.
  */
object UserSession {

  def apply(user: String = VaadinSession.getCurrent.getAttribute(UnlockClaimView.identityAttr).toString): Option[UserSession] = allSessions.get(user)


  def note(nodeId: NodeIdentity, userWallet: Wallet) = synchronized {
    allSessions = allSessions + (nodeId.id -> UserSession(nodeId, userWallet))
  }

  case class UserSession(nodeId: NodeIdentity, userWallet: Wallet)

  private var allSessions: Map[String, UserSession] = Map()
}
