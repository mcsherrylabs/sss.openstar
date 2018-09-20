package sss.asado.nodebuilder

import sss.asado.wallet.{IntegratedWallet, Wallet, WalletPersistence}

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
trait WalletBuilder extends RequireWallet {

  self: RequireNodeIdentity with
    BalanceLedgerBuilder with
    IdentityServiceBuilder with
    WalletPersistenceBuilder with
    BlockChainBuilder with
    RequireDb =>

  lazy val wallet = new Wallet(nodeIdentity,
    balanceLedger,
    identityService,
    walletPersistence,
    currentBlockHeight _)

}

trait IntegratedWalletBuilder {
  self :
    WalletBuilder with
    MessageEventBusBuilder with
    RequireActorSystem =>

  lazy val integratedWallet = new IntegratedWallet(wallet, messageEventBus)
}
