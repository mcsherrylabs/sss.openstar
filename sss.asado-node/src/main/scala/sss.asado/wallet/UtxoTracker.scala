package sss.asado.wallet

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.balanceledger.BalanceLedger.NewUtxo
import sss.asado.network.MessageEventBus

object UtxoTracker {
  def apply(wallet:Wallet)
           (implicit actorSystem: ActorSystem,
            messageEventBus: MessageEventBus): ActorRef = {

    actorSystem.actorOf(Props(classOf[UtxoTracker], wallet, messageEventBus), "UtxoTracker")
  }
}

private class UtxoTracker(wallet:Wallet)(implicit messageEventBus: MessageEventBus) extends Actor with ActorLogging{

  messageEventBus.subscribe(classOf[NewUtxo])

  override def receive: Receive = {

    case NewUtxo(txIndex, txOutput) => {
      wallet
        .toLodgement(txIndex, txOutput)
        .foreach  { l =>
          wallet credit(l)
          log.info("Added to wallet {}, balance now {}", l, wallet.balance())
        }
    }
  }
}
