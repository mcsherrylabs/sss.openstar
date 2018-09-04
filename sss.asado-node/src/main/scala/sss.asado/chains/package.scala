package sss.asado

import sss.asado.ledger.Ledgers

package object chains {

  type GlobalChainIdMask = Int

  case class Chain(id: GlobalChainIdMask, ledgers: Ledgers)

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
