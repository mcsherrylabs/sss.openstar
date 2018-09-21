package sss.asado.identityledger

import java.nio.charset.StandardCharsets.UTF_8

import sss.ancillary.Logging
import sss.asado.ledger._

import scala.util.Try

/**
  * Created by alan on 5/30/16.
  */
class IdentityLedger(ledgerId: Byte, idLedgerService: IdentityService) extends  Ledger with Logging {

  override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {
    require(ledgerItem.ledgerId == ledgerId, s"The ledger id for this (Identity) ledger is $ledgerId but " +
      s"the ledgerItem passed has an id of ${ledgerItem.ledgerId}")

    val ste = ledgerItem.txEntryBytes.toSignedTxEntry

    ste.txEntryBytes.toIdentityLedgerTx match {
      case Claim(identity, pKey) => idLedgerService.claim(identity, pKey)

      case a @ UnLink(identity, tag) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerService.unlink(identity, tag)

      case a @ UnLinkByKey(identity, pKey) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerService.unlink(identity, pKey)

      case a @ Link(identity, pKey, tag) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerService.link(identity, pKey, tag)

      case a @ Rescue(rescuer, identity, pKey, tag) =>
        verifyRescueRequest(rescuer, ste, a, identity)
        idLedgerService.link(identity, pKey, tag)

      case a @ LinkRescuer(rescuer, identity) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerService.linkRescuer(identity, rescuer)

      case a @ UnLinkRescuer(rescuer, identity) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerService.unLinkRescuer(identity, rescuer)
    }
  }

  def verifyRescueRequest(rescuer: String, ste: SignedTxEntry, msg: IdentityLedgerTx, identity: String) {
    require(ste.signatures.nonEmpty && ste.signatures.head.size == 2, "A tag/sig pair must be provided to continue.")

    val rescuers = idLedgerService.rescuers(identity)
    require(rescuers.contains(rescuer), s"This rescuer is not authorized to rescue $identity")

    val tag = new String(ste.signatures.head(0), UTF_8)
    val sig = ste.signatures.head(1)
    val accOpt = idLedgerService.accountOpt(rescuer, tag)
    require(accOpt.isDefined, s"Could not find an account for identity/tag pair ${identity}/$tag provided in signature.")
    require(accOpt.get.verify(sig, msg.txId), "The signature does not match the txId")
  }

  def verifyChangeRequest(ste: SignedTxEntry, msg: IdentityLedgerTx, identity: String) {
    require(ste.signatures.nonEmpty && ste.signatures.head.size == 2, "A tag/sig pair must be provided to continue.")
    val tag = new String(ste.signatures.head(0), UTF_8)
    val sig = ste.signatures.head(1)
    val accOpt = idLedgerService.accountOpt(identity, tag)
    require(accOpt.isDefined, s"Could not find an account for identity/tag pair ${identity}/$tag provided in signature.")
    require(accOpt.get.verify(sig, msg.txId), "The signature does not match the txId")
  }

}
