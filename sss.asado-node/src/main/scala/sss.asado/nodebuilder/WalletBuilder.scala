package sss.asado.nodebuilder

import sss.asado.wallet.{IntegratedWallet, Wallet, WalletPersistence}

/**
  * Created by alan on 6/30/16.
  */

trait WalletPersistenceBuilder {

  self: NodeIdentityBuilder with DbBuilder =>
  lazy val walletPersistence = new WalletPersistence(nodeIdentity.id, db)
}

trait WalletBuilder {

  self: NodeIdentityBuilder with
    BalanceLedgerBuilder with
    IdentityServiceBuilder with
    WalletPersistenceBuilder with
    BlockChainBuilder with
    DbBuilder =>

  lazy val wallet = new Wallet(nodeIdentity,
    balanceLedger,
    identityService,
    walletPersistence,
    currentBlockHeight _)

}

trait IntegratedWalletBuilder {
  self :
    WalletBuilder with
    MessageRouterActorBuilder with
    ActorSystemBuilder =>

  lazy val integratedWallet = new IntegratedWallet(wallet, messageRouterActor)
}
