package sss.openstar.network

object TestMessageEventBusOps {

  implicit class TestMessageEventBus(val msgBus: MessageEventBus) extends AnyVal {

    def simulateNetworkMessage(networkMessage: IncomingSerializedMessage) = {
      msgBus.publish(networkMessage)
    }

  }
}
