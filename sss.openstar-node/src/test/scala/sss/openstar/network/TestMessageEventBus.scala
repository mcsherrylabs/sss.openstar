package sss.openstar.network

import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.{UniqueNodeIdentifier}
import sss.openstar.util.Serialize.ToBytes

object TestMessageEventBusOps {

  implicit class TestMessageEventBus(val msgBus: MessageEventBus) extends AnyVal {

    def simulateNetworkMessage(serializedNetworkMessage: IncomingSerializedMessage): Unit = {
      msgBus.publish(serializedNetworkMessage)
    }

    def simulateNetworkMessage[T](from: UniqueNodeIdentifier, msgKey: Byte, networkMessage: T)
                                  (implicit ev: T => ToBytes, chainId: GlobalChainIdMask): Unit = {

      val serializedNetworkMessage = IncomingSerializedMessage(from,
        SerializedMessage(msgKey, networkMessage))

      simulateNetworkMessage(serializedNetworkMessage)
    }

  }
}
