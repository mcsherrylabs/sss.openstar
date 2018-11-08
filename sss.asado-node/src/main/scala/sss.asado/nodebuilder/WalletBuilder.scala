package sss.asado.nodebuilder

import sss.asado.UniqueNodeIdentifier
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.wallet.{PublicKeyTracker, Wallet, WalletPersistence, WalletTracking}

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
    SendTxBuilder with
    PublicKeyTrackerBuilder =>

  def buildWalletTracking(nodeId: UniqueNodeIdentifier): WalletTracking = {

    new WalletTracking(
      pKTracker.isTracked,
      identityService,
      nodeId,
      walletPersistence
    )
  }

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

