package sss.ui.nobu

import akka.agent.Agent
import sss.asado.account.NodeIdentity
import sss.asado.wallet.Wallet

/**
  * Created by alan on 12/20/16.
  */
object UserSession {

  case class UserSession(nodeId: NodeIdentity, userWallet: Wallet)

  val allSessions: Agent[List[UserSession]] = Agent(List())
}
