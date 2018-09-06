package sss.asado.quorumledger

import java.nio.charset.StandardCharsets

import sss.ancillary.Logging
import sss.asado.identityledger.TaggedPublicKeyAccount
import sss.asado.ledger._


class QuorumLedger(ledgerId: Byte,
                   quorumService: QuorumService,
                   findAccounts: String => Seq[TaggedPublicKeyAccount],
                  )  extends  Ledger with Logging {

  override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {

    require(ledgerItem.ledgerId == ledgerId, s"The ledger id for this (Quorum) ledger is $ledgerId but " +
      s"the ledgerItem passed has an id of ${ledgerItem.ledgerId}")

    val ste = ledgerItem.txEntryBytes.toSignedTxEntry

    ste.txEntryBytes.toQuorumLedgerTx match {
      case msg @ AddNodeId(nodeId) =>
        require(!findAccounts(nodeId).isEmpty, s"Can't add a nodeId that doesn't exist $nodeId")
        val currentSet = quorumService.members()
        val seqDeserialized = deserializeSigLabels(ste.signatures)
        val verified = verifyMembersSignatures(currentSet, msg.txId, seqDeserialized)
        require(verified, s"To add an id to the quorum all current members must correctly sign the tx")
        quorumService.add(nodeId)

      case msg @ RemoveNodeId(nodeId) =>
        val currentMembers = quorumService.members()

        if(currentMembers.size == 1 &&
          currentMembers(0) == nodeId) {
          QuorumLedgerException(s"$nodeId is the last member, cannot remove the last member.")
        }

        val currentSetMinusNodeToBeRemoved = currentMembers.filterNot(_ == nodeId)
        val seqDeserialized = deserializeSigLabels(ste.signatures)
        val verified = verifyMembersSignatures(currentSetMinusNodeToBeRemoved, msg.txId, seqDeserialized)
        require(verified, s"To remove an id from the quorum all (other) current members must correctly sign the tx")
        quorumService.remove(nodeId)
    }
  }


  type DeserializedSig = (String, String, Array[Byte])

  private def deserializeSigLabels(sigs: Seq[Seq[Array[Byte]]]): Seq[DeserializedSig]= {
    for {
      sig <- sigs
      _ = require(sig.size == 3, s"Must have id, tag and signature  - $sig")
    } yield {
      val id = new String(sig(0), StandardCharsets.UTF_8)
      val tag = new String(sig(1), StandardCharsets.UTF_8)
      val sign = sig(2)
      (id, tag, sign)
    }
  }

  private def findSig(member: String, deserialisedSigs: Seq[DeserializedSig]):
  (Seq[DeserializedSig], Seq[DeserializedSig]) = {
    deserialisedSigs.partition(_._1 == member)
  }


  private def verifySig(member: String, msg:Array[Byte], deSig: DeserializedSig): Boolean = {

    val id = deSig._1
    val tag = deSig._2
    val sig = deSig._3

    val acc = findAccounts(id).find(_.tag == tag)
      .getOrElse(
        QuorumLedgerException(s"No key found for account/tag $id/$tag")
      )
    acc.account.verify(sig, msg)

  }

  private def verifyMembersSignatures(members: Seq[String],
                                      msg: Array[Byte],
                                      sigs: Seq[DeserializedSig]): Boolean = {

      (members, sigs) match {
        case (Nil, _) => true
        case (members, Nil) => false
        case (member +: restMembers, sigs) =>
          val (foundSeq, remainingSigs) = findSig(member, sigs)
          require(foundSeq.headOption.isDefined, s"Quorum ledger no signature found for member $member")
          val verified = verifySig(member, msg, foundSeq.head)
          require(verified, s"Altering quorum member $member failed, ${foundSeq.head} signature verification failed")
          verifyMembersSignatures(restMembers, msg, remainingSigs)

      }

  }

}


