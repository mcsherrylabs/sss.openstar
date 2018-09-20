package sss.asado.network

import sss.asado.UniqueNodeIdentifier

trait NetSend extends ((SerializedMessage, Set[UniqueNodeIdentifier]) => Unit) {
  def apply(v1: SerializedMessage, v2: UniqueNodeIdentifier): Unit = apply(v1, Set(v2))
}

