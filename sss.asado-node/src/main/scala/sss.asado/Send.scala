package sss.asado

import sss.asado.MessageKeys.messages
import sss.asado.Send.ToSerializedMessage
import sss.asado.chains.Chains.GlobalChainIdMask
import sss.asado.eventbus.PureEvent
import sss.asado.network.{NetSend, SerializedMessage}
import sss.asado.util.Serialize.ToBytes

object Send {

  def apply (ns: NetSend,
             makeSerializedMessage: ToSerializedMessage = Send.ToSerializedMessageImpl): Send =
    new Send(ns, makeSerializedMessage)


  trait ToSerializedMessage {
    def apply[T](msgCode: Byte, t: T)
                             (implicit chainId: GlobalChainIdMask, view: T => ToBytes): SerializedMessage

    def apply(msgCode: Byte)(implicit chainId: GlobalChainIdMask): SerializedMessage
  }

  object ToSerializedMessageImpl extends ToSerializedMessage {

    def apply(msgCode: Byte)(implicit chainId: GlobalChainIdMask): SerializedMessage = {
      //SerializedMessage(msgCode)
      SerializedMessage(chainId, msgCode, Array())
    } ensuring (messages.find(msgCode).get.clazz == classOf[PureEvent],
      s"No bytes were provided but code $msgCode does not map to a PureEvent class")

    def apply[T](msgCode: Byte, t: T)
                             (implicit chainId: GlobalChainIdMask, view: T => ToBytes): SerializedMessage = {
      SerializedMessage(msgCode, t)
    } ensuring(messages.find(msgCode).get.clazz.isAssignableFrom(t.getClass),
      s"The class to encode doesn't match the msgCode type ${t.getClass} $msgCode ")

  }
}


class Send private (ns: NetSend, makeSerializedMessage: ToSerializedMessage = Send.ToSerializedMessageImpl) {


  def apply(msgCode: Byte, nId: UniqueNodeIdentifier)
           (implicit chainId: GlobalChainIdMask): Unit = {
    apply(msgCode, Set(nId))
  }

  def apply(msgCode: Byte, nIds: Set[UniqueNodeIdentifier])
           (implicit chainId: GlobalChainIdMask): Unit = {

    ns(makeSerializedMessage(msgCode), nIds)
  }

  def apply[T](msgCode: Byte, a: T, nId: UniqueNodeIdentifier)
                         (implicit chainId: GlobalChainIdMask, view: T => ToBytes): Unit = {
    apply(msgCode, a, Set(nId))
  }

  def apply[T](msgCode: Byte, a: T, nIds: Set[UniqueNodeIdentifier])
                         (implicit chainId: GlobalChainIdMask, view: T => ToBytes): Unit = {

    ns(makeSerializedMessage[T](msgCode, a), nIds)
  }
}
