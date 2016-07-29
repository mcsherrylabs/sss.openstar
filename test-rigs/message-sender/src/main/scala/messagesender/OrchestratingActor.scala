package messagesender

import akka.actor.{Actor, ActorLogging, Props}
import messagesender.CheckInBoxForCash.CheckInBox
import sss.asado.MessageKeys
import sss.asado.message.MessageInBox

import sss.asado.network.MessageRouter.RegisterRef

import sss.asado.state.AsadoStateProtocol.StateMachineInitialised
import sss.asado.state.AsadoState.ReadyState

/**
  * Created by alan on 7/28/16.
  */
class OrchestratingActor(client: MessageSenderClient, prefix:String, circSeq: CircularSeq) extends Actor with ActorLogging  {
  import client._

  override def receive: Receive = {
    case StateMachineInitialised =>
      startNetwork
      connectHome


    case ReadyState =>
      val inBox = MessageInBox(nodeIdentity.id)

      val messageSendingActorRef = context.system.actorOf(Props(classOf[MessageSendingActor], client, inBox, prefix, circSeq))
      val checkInBoxForCashRef = context.system.actorOf(Props(classOf[CheckInBoxForCash],
        inBox, identityService, nodeIdentity, ncRef, wallet, homeDomain))

      messageRouterActor ! RegisterRef( MessageKeys.SignedTxAck, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.AckConfirmTx, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.SignedTxNack, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.MessageResponse, messageSendingActorRef)

      messageSendingActorRef ! TrySendMail
      checkInBoxForCashRef ! CheckInBox


  }

}
