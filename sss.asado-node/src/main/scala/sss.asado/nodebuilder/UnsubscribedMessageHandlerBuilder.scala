package sss.asado.nodebuilder

import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.{MessageKeys, PublishedMessageKeys, UniqueNodeIdentifier}
import sss.asado.network.MessageEventBus.{IncomingMessage, Unsubscribed, UnsubscribedEvent, UnsubscribedIncomingMessage}
import sss.asado.network.{MessageEventBus, NetworkRef, SerializedMessage}

trait UnsubscribedMessageHandlerBuilder {

  self : NetworkControllerBuilder with
          NodeIdentityBuilder with
          RequireActorSystem with
          MessageEventBusBuilder =>

  lazy val startUnsubscribedHandler: ActorRef = {
    DefaultMessageHandlerActor(net, messageEventBus, nodeIdentity.id)
  }

  private object DefaultMessageHandlerActor {

    def apply(net: NetworkRef,
              messageEventBus: MessageEventBus,
              thisNodeId: UniqueNodeIdentifier
             )(implicit actorSystem: ActorSystem): ActorRef = {

      actorSystem.actorOf(
        Props(classOf[DefaultMessageHandlerImpl],
          net,
          thisNodeId,
          messageEventBus)
      )

    }
  }

}


private class DefaultMessageHandlerImpl(net: NetworkRef,
                                        thisNodeId: UniqueNodeIdentifier,
                                        messageEventBus: MessageEventBus)
  extends Actor with
    ActorLogging {

  messageEventBus.subscribe(classOf[Unsubscribed])

  override def receive: Receive = {

    case UnsubscribedIncomingMessage(IncomingMessage(chainId, msgCode, clientNodeId, _))
      if(msgCode != MessageKeys.GenericErrorMessage) =>

      net.send(SerializedMessage(chainId, MessageKeys.GenericErrorMessage,
        (s"$thisNodeId received your msg (code=$msgCode) " +
          "but is not processing at this time").getBytes(StandardCharsets.UTF_8)),
        clientNodeId)

    case x =>
      log.warning("Event has no sink! {} ", x)

  }
}
