package sss.openstar.message


import sss.openstar.account.NodeIdentity
import sss.openstar.balanceledger.Tx
import sss.openstar.contract.{SaleOrReturnSecretEnc, SingleIdentityEnc}
import sss.openstar.identityledger.IdentityService


/**
  * Created by alan on 6/24/16.
  */
class MessagePaywall(minNumBlocksInFuture: Int, chargePerMessage: Int,
                     currentBlockHeight: () => Long,
                     nodeId: NodeIdentity, identityService: IdentityService) {

  class MessagePaywallException(msg: String) extends Exception(msg)

  @throws[MessagePaywallException]
  def require(f: => Boolean, msg:String) = if(!f) throw new MessagePaywallException(msg)

  @throws[MessagePaywallException]
  def require(msg:String) = throw new MessagePaywallException(msg)

  def validate(tx: Tx): String = {

    require(tx.outs.size >= 2, "There must be at least 2 outputs on the payment.")
    val adjustedIndices = Seq(tx.outs.size - 2, tx.outs.size - 1)
    // adjust this as there may be a change tx in position 0.
    val servicePayment = tx.outs(adjustedIndices(0))

    require(servicePayment.amount >= chargePerMessage,
      s"Message delivery charge is $chargePerMessage. (${servicePayment.amount})")

    servicePayment.encumbrance match {
      case SingleIdentityEnc(id, blockHeight) if nodeId.id == id && blockHeight == 0 =>
      case _ => require("Payment to service must be of type SingleIdentityEnc with blockHeight 0")
    }

    val receiverPayment = tx.outs(adjustedIndices(1))
    receiverPayment.encumbrance match {
      case SaleOrReturnSecretEnc(returnIdentity, claimant,hashOfSecret,returnBlockHeight) =>
        require(identityService.accounts(claimant).nonEmpty, s"The claimant identity must exist (${claimant})")
        require(identityService.accounts(returnIdentity).nonEmpty, s"The return identity must exist (${returnIdentity})")
        require(currentBlockHeight() + minNumBlocksInFuture <= returnBlockHeight,
          s"The return block height must be at least $minNumBlocksInFuture in the future")
        claimant
      case _ => require(s"Only SaleOrReturnSecretEnc is acceptable")

    }

  }
}
