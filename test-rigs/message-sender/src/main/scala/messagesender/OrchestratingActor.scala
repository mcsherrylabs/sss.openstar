package messagesender

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import messagesender.CheckInBoxForCash.CheckInBox
import sss.asado.MessageKeys
import sss.asado.message.MessageInBox
import sss.asado.network.MessageRouter.RegisterRef
import sss.asado.state.AsadoStateProtocol.StateMachineInitialised
import sss.asado.state.AsadoState.{ConnectingState, OrderedState, ReadyState}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._

/**
  * Created by alan on 7/28/16.
  */
class OrchestratingActor(client: MessageSenderClient, prefix:String, circSeq: CircularSeq) extends Actor with ActorLogging  {
  import client._

  private case object ConnectHome
  private var cancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case StateMachineInitialised =>
      startNetwork

    case ConnectHome =>
      connectHome
      cancellable = Option(context.system.scheduler.scheduleOnce(
        FiniteDuration(1, MINUTES),
        self, ConnectHome))

    case ConnectingState =>
      self ! ConnectHome

    case OrderedState => cancellable.map(_.cancel())

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
