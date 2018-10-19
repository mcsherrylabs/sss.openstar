package sss.asado.nodebuilder

import sss.asado.account.NodeIdentity
import sss.asado.wallet.{IntegratedWallet, PublicKeyTracker, Wallet, WalletPersistence}

/**
  * Created by alan on 6/30/16.
  */

trait WalletPersistenceBuilder {

  self: NodeIdentityBuilder with RequireDb =>
  lazy val walletPersistence = new WalletPersistence(nodeIdentity.id, db)
}

trait RequireWallet {
  val wallet: Wallet
}

trait PublicKeyTrackerBuilder {
  self: RequireNodeIdentity with
    RequireDb =>

  lazy val pKTracker: PublicKeyTracker = new PublicKeyTracker(nodeIdentity.id)
}

trait WalletBuilder extends RequireWallet {

  self: RequireNodeIdentity with
    BalanceLedgerBuilder with
    IdentityServiceBuilder with
    WalletPersistenceBuilder with
    BlockChainBuilder with
    RequireDb with
    PublicKeyTrackerBuilder =>

  def buildWallet(nodeIdentity: NodeIdentity): Wallet =

    new Wallet(nodeIdentity,
      balanceLedger,
      identityService,
      walletPersistence,
      currentBlockHeight _,
      pKTracker.isTracked
    )

  lazy val wallet = buildWallet(nodeIdentity)

}

trait IntegratedWalletBuilder {
  self :
    WalletBuilder with
    MessageEventBusBuilder with
    RequireActorSystem =>

  lazy val integratedWallet = new IntegratedWallet(wallet, messageEventBus)
}
