package sss.asado

import sss.asado.ledger.Ledgers
import sss.asado.network.UniqueNodeIdentifier

package object chains {

  type GlobalChainIdMask = Int

  trait Chain {
    val id: GlobalChainIdMask
    implicit val ledgers: Ledgers
    def quorumMembers(): Seq[UniqueNodeIdentifier]
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
}
