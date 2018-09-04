package sss.asado.identityledger

import java.nio.charset.StandardCharsets.UTF_8

import sss.ancillary.Logging
import sss.asado.{Identity, IdentityTag, PublishedMessageKeys}
import sss.asado.ledger._


class IdentityLedger(
                      idLedgerStorage: IdentityService
                    ) extends Ledger
  with Logging {

  val id: LedgerId = PublishedMessageKeys.IdentityLedger

  override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {
    require(ledgerItem.ledgerId == id, s"The ledger id for this (Identity) ledger is $id but " +
      s"the ledgerItem passed has an id of ${ledgerItem.ledgerId}")

    val ste = ledgerItem.txEntryBytes.toSignedTxEntry

    ste.txEntryBytes.toIdentityLedgerMessage match {
      case Claim(identity, pKey) => idLedgerStorage.claim(identity, pKey)

      case a @ UnLink(identity, tag) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerStorage.unlink(identity, tag)

      case a @ UnLinkByKey(identity, pKey) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerStorage.unlink(identity, pKey)

      case a @ Link(identity, pKey, tag) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerStorage.link(identity, pKey, tag)

      case a @ Rescue(rescuer, identity, pKey, tag) =>
        verifyRescueRequest(rescuer, ste, a, identity)
        idLedgerStorage.link(identity, pKey, tag)

      case a @ LinkRescuer(rescuer, identity) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerStorage.linkRescuer(identity, rescuer)

      case a @ UnLinkRescuer(rescuer, identity) =>
        verifyChangeRequest(ste, a, identity)
        idLedgerStorage.unLinkRescuer(identity, rescuer)
    }
  }

  def verifyRescueRequest(rescuer: Identity, ste: SignedTxEntry, msg: IdentityLedgerMessage, identity: Identity) {
    require(ste.signatures.nonEmpty && ste.signatures.head.size == 2, "A tag/sig pair must be provided to continue.")

    val rescuers = idLedgerStorage.rescuers(identity)
    require(rescuers.contains(rescuer), s"This rescuer is not authorized to rescue $identity")

    val tag = IdentityTag(new String(ste.signatures.head(0), UTF_8))
    val sig = ste.signatures.head(1)
    val accOpt = idLedgerStorage.accountOpt(rescuer, tag)
    require(accOpt.isDefined, s"Could not find an account for identity/tag pair ${identity}/$tag provided in signature.")
    require(accOpt.get.verify(sig, msg.txId), "The signature does not match the txId")
  }

  def verifyChangeRequest(ste: SignedTxEntry, msg: IdentityLedgerMessage, identity: Identity) {
    require(ste.signatures.nonEmpty && ste.signatures.head.size == 2, "A tag/sig pair must be provided to continue.")
    val tag = IdentityTag(new String(ste.signatures.head(0), UTF_8))
    val sig = ste.signatures.head(1)
    val accOpt = idLedgerStorage.accountOpt(identity, tag)
    require(accOpt.isDefined, s"Could not find an account for identity/tag pair ${identity}/$tag provided in signature.")
    require(accOpt.get.verify(sig, msg.txId), "The signature does not match the txId")
  }
}
