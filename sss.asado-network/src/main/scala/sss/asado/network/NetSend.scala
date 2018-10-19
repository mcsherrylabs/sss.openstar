package sss.asado.network

import sss.asado.UniqueNodeIdentifier

trait NetSend extends ((SerializedMessage, Set[UniqueNodeIdentifier]) => Unit) {
  def apply(sm: SerializedMessage, nId: UniqueNodeIdentifier): Unit = apply(sm, Set(nId))
}

