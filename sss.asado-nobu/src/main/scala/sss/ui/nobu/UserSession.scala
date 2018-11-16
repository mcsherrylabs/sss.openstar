package sss.ui.nobu

import java.util.concurrent.atomic.AtomicReference

import com.vaadin.server.VaadinSession
import sss.asado.account.NodeIdentity
import sss.asado.wallet.Wallet

/**
  * Created by alan on 12/20/16.
  */
object UserSession {

  def apply(user: String): Option[UserSession] = allSessions.get().get(user)


  def note(nodeId: NodeIdentity, userWallet: Wallet) = {
    allSessions.updateAndGet(
      _ + (nodeId.id -> UserSession(nodeId, userWallet))
    )
  }


  case class UserSession(nodeId: NodeIdentity, userWallet: Wallet)

  private val allSessions: AtomicReference[Map[String, UserSession]] = new AtomicReference(Map())
}
