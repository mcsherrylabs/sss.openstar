package sss.openstar.nodebuilder

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.openstar.chains.Chains.GlobalChainIdMask
import sss.openstar.eventbus.StringMessage
import sss.openstar.{MessageKeys, Send, UniqueNodeIdentifier}
import sss.openstar.network.MessageEventBus.{IncomingMessage, Unsubscribed, UnsubscribedIncomingMessage}
import sss.openstar.network.MessageEventBus

trait UnsubscribedMessageHandlerBuilder {

  self : RequireNetSend with
          NodeIdentityBuilder with
          RequireActorSystem with
          MessageEventBusBuilder =>

  lazy val startUnsubscribedHandler: ActorRef = {
    DefaultMessageHandlerActor(send, messageEventBus, nodeIdentity.id)
  }

  private object DefaultMessageHandlerActor {

    def apply(send: Send,
              messageEventBus: MessageEventBus,
              thisNodeId: UniqueNodeIdentifier
             )(implicit actorSystem: ActorSystem): ActorRef = {

      actorSystem.actorOf(
        Props(classOf[DefaultMessageHandlerImpl],
          send,
          thisNodeId,
          messageEventBus)
      , "DefaultMessageHandlerActor")

    }
  }

}


private class DefaultMessageHandlerImpl(send: Send,
                                        thisNodeId: UniqueNodeIdentifier,
                                        messageEventBus: MessageEventBus)
  extends Actor with
    ActorLogging {

  messageEventBus.subscribe(classOf[Unsubscribed])


  override def receive: Receive = {

    case UnsubscribedIncomingMessage(IncomingMessage(chainId: GlobalChainIdMask, msgCode, clientNodeId, _))
      if(msgCode != MessageKeys.GenericErrorMessage) =>

        implicit val c = chainId

        send.apply(MessageKeys.GenericErrorMessage,

          StringMessage(s"$thisNodeId received your msg (code=$msgCode) " +
            "but is not processing at this time")

        ,clientNodeId)


    case x =>
      log.warning("Event has no sink! {} ", x)

  }
}
