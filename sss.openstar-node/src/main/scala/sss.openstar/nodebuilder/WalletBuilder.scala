package sss.openstar.nodebuilder

import sss.openstar.UniqueNodeIdentifier
import sss.openstar.account.NodeIdentity
import sss.openstar.balanceledger.{TxIndex, TxOutput}
import sss.openstar.wallet.{PublicKeyTracker, Wallet, WalletIndexTracker, WalletPersistence}

/**
  * Created by alan on 6/30/16.
  */

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
    BlockChainBuilder with
    RequireDb with
    SendTxBuilder with
    PublicKeyTrackerBuilder =>

  def buildWalletPersistence(nodeId: UniqueNodeIdentifier): WalletPersistence =
    new WalletPersistence(nodeId, db)

  def buildWalletIndexTracker(nodeId: UniqueNodeIdentifier): WalletIndexTracker = {

    new WalletIndexTracker(
      pKTracker.isTracked,
      identityService,
      nodeId,
      buildWalletPersistence(nodeId)
    )
  }

  def buildWallet(nodeIdentity: NodeIdentity): Wallet =

    new Wallet(nodeIdentity,
      balanceLedger,
      identityService,
      buildWalletPersistence(nodeIdentity.id),
      currentBlockHeight _,
      pKTracker.isTracked
    )

  lazy val wallet = buildWallet(nodeIdentity)

}

