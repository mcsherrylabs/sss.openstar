package sss.openstar.chains

import sss.openstar.UniqueNodeIdentifier
import sss.openstar.chains.Chains.{Chain, GlobalChainIdMask}
import sss.openstar.ledger.Ledgers


object Chains {

  type GlobalChainIdMask = Byte

  trait Chain {
    implicit val id: GlobalChainIdMask
    implicit val ledgers: Ledgers
    def quorumCandidates(): Set[UniqueNodeIdentifier]
  }

}

class Chains(chains: Seq[Chain]) {

  //Note the order of val initialisation is important.
  val ordered: Seq[Chain] = {
    chains.sortWith(_.id < _.id)
  }

  val byId: Map[GlobalChainIdMask, Chain] = {
    (ordered map (l => l.id -> l))
      .toMap
  }

  assert(byId.size == ordered.size, "Check the chains parameter for duplicate ids...")

  def apply(): Seq[Chain] = ordered
  def apply(id: GlobalChainIdMask) = byId(id)
  def get(id: GlobalChainIdMask)= byId.get(id)
}
