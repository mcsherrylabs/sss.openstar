package sss.asado.quorumledger

import java.nio.charset.StandardCharsets

import sss.ancillary.Logging
import sss.asado.{AsadoEvent, UniqueNodeIdentifier}
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.eventbus.EventPublish
import sss.asado.identityledger.TaggedPublicKeyAccount
import sss.asado.ledger._
import sss.asado.quorumledger.QuorumLedger.NewQuorumCandidates

import scala.util.Try


object QuorumLedger {
  case class NewQuorumCandidates(uniqueId: GlobalChainIdMask, candidates: Set[UniqueNodeIdentifier]) extends AsadoEvent
}

class QuorumLedger(chainId: GlobalChainIdMask,
                   ledgerId: Byte,
                   quorumService: QuorumService,
                   eventPublish: EventPublish,
                   findAccounts: String => Seq[TaggedPublicKeyAccount],
                  )  extends  Ledger with Logging {

  require(chainId == quorumService.uniqueChainId,
    s"Mismatched chain Id is quorum service (${quorumService.uniqueChainId}) and ledger {$chainId}")


  override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {

    require(ledgerItem.ledgerId == ledgerId, s"The ledger id for this (Quorum) ledger is $ledgerId but " +
      s"the ledgerItem passed has an id of ${ledgerItem.ledgerId}")

    val ste = ledgerItem.txEntryBytes.toSignedTxEntry

    ste.txEntryBytes.toQuorumLedgerTx match {
      case msg @ AddNodeId(nodeId) =>
        require(!findAccounts(nodeId).isEmpty, s"Can't add a nodeId that doesn't exist $nodeId")
        val currentSet = quorumService.candidates()
        val seqDeserialized = deserializeSigLabels(ste.signatures)
        val verified = verifyMembersSignatures(currentSet.toSeq, msg.txId, seqDeserialized)
        require(verified, s"To add an id to the quorum all current members must correctly sign the tx")
        val m = quorumService.add(nodeId)
        eventPublish.publish(NewQuorumCandidates(chainId, m))

      case msg @ RemoveNodeId(nodeId) =>
        val currentMembers = quorumService.candidates()

        if(currentMembers.size == 1 &&
          currentMembers.head == nodeId) {
          QuorumLedgerException(s"$nodeId is the last member, cannot remove the last member.")
        }

        val currentSetMinusNodeToBeRemoved = currentMembers.filterNot(_ == nodeId)
        val seqDeserialized = deserializeSigLabels(ste.signatures)
        val verified = verifyMembersSignatures(currentSetMinusNodeToBeRemoved.toSeq, msg.txId, seqDeserialized)
        require(verified, s"To remove an id from the quorum all (other) current members must correctly sign the tx")
        val m = quorumService.remove(nodeId)
        eventPublish.publish(NewQuorumCandidates(chainId, m))

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

  private def findSig(member: UniqueNodeIdentifier, deserialisedSigs: Seq[DeserializedSig]):
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

  private def verifyMembersSignatures(members: Seq[UniqueNodeIdentifier],
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


