package sss.asado.wallet

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import sss.asado.{AsadoEvent, UniqueNodeIdentifier}
import sss.asado.balanceledger.BalanceLedger.NewUtxo
import sss.asado.network.MessageEventBus
import sss.asado.wallet.UtxoTracker.{NewLodgement, NewWallet}
import sss.asado.wallet.WalletPersistence.Lodgement

object UtxoTracker {

  case class NewLodgement(nodeId: UniqueNodeIdentifier, l: Lodgement) extends AsadoEvent

  case class NewWallet(walletTracking:WalletIndexTracker) extends AsadoEvent

  def apply(walletTracking:WalletIndexTracker)
           (implicit actorSystem: ActorSystem,
            messageEventBus: MessageEventBus): ActorRef = {

    actorSystem.actorOf(
      Props(classOf[UtxoTracker], walletTracking, messageEventBus)
        .withDispatcher("blocking-dispatcher"), "UtxoTracker")
  }
}

private class UtxoTracker(walletTracking: WalletIndexTracker)(implicit messageEventBus: MessageEventBus) extends Actor with ActorLogging{

  messageEventBus.subscribe(classOf[NewUtxo])
  messageEventBus.subscribe(classOf[NewWallet])

  override def receive = withWallets(Seq(walletTracking))

  private def withWallets(wallets: Seq[WalletIndexTracker]): Receive = {

    case NewWallet(w) =>
      val newWallets = w +: (wallets filterNot (_.id == w.id))
      context become withWallets(newWallets)

    case NewUtxo(txIndex, txOutput) => {
      wallets foreach { wallet =>
        wallet(txIndex, txOutput)
          .foreach(l => messageEventBus.publish(NewLodgement(wallet.id, l)))
      }
    }
  }
}
