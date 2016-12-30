package sss.ui.nobu

import sss.asado.account.NodeIdentity
import sss.asado.wallet.Wallet

/**
  * Created by alan on 12/20/16.
  */
object UserSession {

  def apply(user: String): UserSession = allSessions(user)


  def note(nodeId: NodeIdentity, userWallet: Wallet) = {
    allSessions = allSessions + (nodeId.id -> UserSession(nodeId, userWallet))
  }

  case class UserSession(nodeId: NodeIdentity, userWallet: Wallet)

  private var allSessions: Map[String, UserSession] = Map()
}
