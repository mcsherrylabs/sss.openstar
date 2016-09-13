package messagesender

import akka.actor.{Actor, ActorLogging, Cancellable}
import sss.asado.actor.AsadoEventSubscribedActor
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.block.BlockChainTxId
import sss.asado.contract.SingleIdentityEnc
import sss.asado.state.AsadoStateProtocol.{ReadyStateEvent, StateMachineInitialised}
import sss.asado.wallet.IntegratedWallet.{Payment, TxFailure, TxSuccess}
import sss.asado.wallet.WalletPersistence.Lodgement

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by alan on 7/28/16.
  */
class OrchestratingActor(client: MessageSenderClient, prefix:String, circSeq: CircularSeq) extends Actor
  with ActorLogging with AsadoEventSubscribedActor  {
  import client._

  private case object ConnectHome
  private var cancellable: Option[Cancellable] = None

  override def receive: Receive = {
    case StateMachineInitialised =>
      startNetwork
      val bal = integratedWallet.balance
      log.info(s"StateMachineInitialised balance is $bal")
      context.system.scheduler.scheduleOnce(
        FiniteDuration(5, SECONDS),
        self, ConnectHome)

    case ConnectHome => connectHome

    case ReadyStateEvent =>


      /*val inBox = MessageInBox(nodeIdentity.id)

      val messageSendingActorRef = context.system.actorOf(Props(classOf[MessageSendingActor], client, inBox, prefix, circSeq))
      val checkInBoxForCashRef = context.system.actorOf(Props(classOf[CheckInBoxForCash],
        inBox, identityService, nodeIdentity, ncRef, wallet, homeDomain))

      messageRouterActor ! RegisterRef( MessageKeys.SignedTxAck, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.AckConfirmTx, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.TempNack, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.SignedTxNack, checkInBoxForCashRef)
      messageRouterActor ! RegisterRef( MessageKeys.MessageResponse, messageSendingActorRef)

      messageSendingActorRef ! TrySendMail
      checkInBoxForCashRef ! CheckInBox*/
      implicit val timeout = Duration(3, MINUTES)

      val payment = Payment(client.nodeIdentity.id, 1)

      val bal = integratedWallet.balance
      log.info(s"About to start balance is $bal")
      if(bal > 0) {
        for (j <- 0 until 5000) {
          for (i <- 0 until 5000) {
            integratedWallet.pay(payment) match {
              case TxSuccess(blockChainTxId: BlockChainTxId, txIndex: TxIndex, txIdentifier: Option[String]) =>
                val txOutput = TxOutput(1, SingleIdentityEnc(client.nodeIdentity.id, 0))
                integratedWallet.credit(Lodgement(txIndex, txOutput, blockChainTxId.height))
              case TxFailure(txMessage, txIdentifier) =>
                log.error(s"PROBLEM: ${txMessage}")

            }
          }
        }
      }

  }

}
