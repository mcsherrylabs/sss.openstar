package sss.ui.nobu

import sss.asado.UniqueNodeIdentifier

object ValidateBounty {
  /*
  Trivial acceptance of messages if the amount is greater than 0
  no matter who they are from
   */
  def validateBounty(amount: Long, from: UniqueNodeIdentifier): Boolean = amount > 0

}
