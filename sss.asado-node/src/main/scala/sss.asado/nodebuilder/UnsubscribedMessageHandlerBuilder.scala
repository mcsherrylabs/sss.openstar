package sss.asado.nodebuilder

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.{MessageKeys, PublishedMessageKeys, UniqueNodeIdentifier}
import sss.asado.network.MessageEventBus.{IncomingMessage, Unsubscribed, UnsubscribedEvent, UnsubscribedIncomingMessage}
import sss.asado.network.{MessageEventBus, NetSendTo, NetworkRef, SerializedMessage}

trait UnsubscribedMessageHandlerBuilder {

  self : RequireNetSend with
          NodeIdentityBuilder with
          RequireActorSystem with
          MessageEventBusBuilder =>

  lazy val startUnsubscribedHandler: ActorRef = {
    DefaultMessageHandlerActor(sendTo, messageEventBus, nodeIdentity.id)
  }

  private object DefaultMessageHandlerActor {

    def apply(send: NetSendTo,
              messageEventBus: MessageEventBus,
              thisNodeId: UniqueNodeIdentifier
             )(implicit actorSystem: ActorSystem): ActorRef = {

      actorSystem.actorOf(
        Props(classOf[DefaultMessageHandlerImpl],
          send,
          thisNodeId,
          messageEventBus)
      )

    }
  }

}


private class DefaultMessageHandlerImpl(send: NetSendTo,
                                        thisNodeId: UniqueNodeIdentifier,
                                        messageEventBus: MessageEventBus)
  extends Actor with
    ActorLogging {

  messageEventBus.subscribe(classOf[Unsubscribed])

  override def receive: Receive = {

    case UnsubscribedIncomingMessage(IncomingMessage(chainId, msgCode, clientNodeId, _))
      if(msgCode != MessageKeys.GenericErrorMessage) =>

      send(SerializedMessage(chainId, MessageKeys.GenericErrorMessage,
        (s"$thisNodeId received your msg (code=$msgCode) " +
          "but is not processing at this time").getBytes(StandardCharsets.UTF_8)),
        clientNodeId)

    case x =>
      log.warning("Event has no sink! {} ", x)

  }
}
