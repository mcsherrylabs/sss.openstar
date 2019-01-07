package sss.openstar.network

trait NetConnect {
  def connect(nId: NodeId,reconnectStrategy: ReconnectionStrategy): Unit
  def apply(nId: NodeId,reconnectStrategy: ReconnectionStrategy): Unit = connect(nId, reconnectStrategy)
}

