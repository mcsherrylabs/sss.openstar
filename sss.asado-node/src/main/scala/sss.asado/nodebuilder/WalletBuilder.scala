package sss.asado.nodebuilder

import sss.asado.UniqueNodeIdentifier
import sss.asado.account.NodeIdentity
import sss.asado.balanceledger.{TxIndex, TxOutput}
import sss.asado.wallet.{PublicKeyTracker, Wallet, WalletPersistence, WalletIndexTracker}

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

